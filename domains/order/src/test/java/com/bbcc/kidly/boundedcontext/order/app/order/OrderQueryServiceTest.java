package com.bbcc.kidly.boundedcontext.order.app.order;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryService;
import com.bbcc.kidly.boundedcontext.delivery.app.config.PartialCancelProperties;
import com.bbcc.kidly.boundedcontext.delivery.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.delivery.domain.Delivery;
import com.bbcc.kidly.boundedcontext.delivery.out.order.DeliveryOrdersAdapter;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.AdminOrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.provided.ProvidedOrderService;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.order.out.delivery.OrderDeliveriesAdapter;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderQueryServiceTest {

    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final Long OPTION_ID = 1L;
    private static final Long ORDER_ID = 7L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private SellerReplicaRepository sellerReplicaRepository;
    @Mock
    private OrderStatusHistoryRepository historyRepository;

    private OrderQueryService orderQueries;

    @BeforeEach
    void setUp() {
        ProvidedOrderService provided = new ProvidedOrderService(orderRepository, historyRepository, POLICY);
        DeliveryService delivery = new DeliveryService(deliveryRepository,
            new DeliveryOrdersAdapter(provided, provided, null), new PartialCancelProperties(false), event -> {
            });
        orderQueries = new OrderQueryService(orderRepository, new OrderDeliveriesAdapter(delivery, delivery),
            sellerReplicaRepository, historyRepository);
    }

    @Nested
    @DisplayName("관리자 전체 주문 목록")
    class GetAllOrders {

        @Test
        @DisplayName("모든 회원 주문을 저장소 순서(주문 ID 내림차순)대로, 주문상품을 한 번에 읽어 회원 · 취소 사유 · 주문상품 수와 함께 돌려준다")
        void returnsAllOrders() {
            Order paid = withId(placedOrderWithTwoItems(), 8L);
            Order cancelled = withId(withItemIds(placedOrder(3L)), 7L);
            cancelled.cancel(Order.CancelReason.PAYMENT_EXPIRED);
            PageRequest pageable = PageRequest.of(0, 20);
            when(orderRepository.findAllByOrderByIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(paid, cancelled), pageable, 2));
            when(orderRepository.findAllWithItemsByIdIn(List.of(8L, 7L))).thenReturn(List.of(cancelled, paid));

            Page<AdminOrderSummary> page = orderQueries.getAllOrders(0, 20);

            assertThat(page.getContent()).extracting(AdminOrderSummary::orderId, AdminOrderSummary::memberId,
                    AdminOrderSummary::status, AdminOrderSummary::cancelReason, AdminOrderSummary::itemCount)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(8L, BUYER_ID, "PENDING_PAYMENT", null, 2),
                    org.assertj.core.groups.Tuple.tuple(7L, 3L, "CANCELLED", "PAYMENT_EXPIRED", 1));
            assertThat(page.getContent().getFirst().totalAmount()).isEqualTo(80_000L);
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("페이지에는 있는데 주문상품 조회에 없는 주문은 예외 없이 주문상품 수 0 · 나머지 값 그대로, WARN 한 줄")
        void orderWithoutLoadedItemsHasZeroItemCount(CapturedOutput output) {
            Order paid = withId(placedOrderWithTwoItems(), 8L);
            Order missing = withId(withItemIds(placedOrder(3L)), 7L);
            missing.cancel(Order.CancelReason.PAYMENT_EXPIRED);
            PageRequest pageable = PageRequest.of(0, 20);
            when(orderRepository.findAllByOrderByIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(paid, missing), pageable, 2));
            when(orderRepository.findAllWithItemsByIdIn(List.of(8L, 7L))).thenReturn(List.of(paid));

            Page<AdminOrderSummary> page = orderQueries.getAllOrders(0, 20);

            assertThat(page.getContent()).extracting(AdminOrderSummary::orderId, AdminOrderSummary::memberId,
                    AdminOrderSummary::status, AdminOrderSummary::cancelReason, AdminOrderSummary::totalAmount,
                    AdminOrderSummary::itemCount)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(8L, BUYER_ID, "PENDING_PAYMENT", null, 80_000L, 2),
                    org.assertj.core.groups.Tuple.tuple(7L, 3L, "CANCELLED", "PAYMENT_EXPIRED", 50_000L, 0));
            assertThat(output).containsPattern("WARN.*관리자 주문 목록.*reason=order-items-not-found orderId=7");
            assertThat(output.getAll().lines().filter(line -> line.contains("reason=order-items-not-found")).count())
                .isEqualTo(1);
        }

        @Test
        @DisplayName("주문이 없으면 빈 목록이고 주문상품은 읽지 않는다")
        void emptyPageDoesNotLoadItems() {
            PageRequest pageable = PageRequest.of(0, 20);
            when(orderRepository.findAllByOrderByIdDesc(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<AdminOrderSummary> page = orderQueries.getAllOrders(0, 20);

            assertThat(page.getContent()).isEqualTo(List.of());
            assertThat(page.getTotalElements()).isEqualTo(0L);
            verify(orderRepository, never()).findAllWithItemsByIdIn(any());
        }

        @Test
        @DisplayName("offset(page × size)이 int 최댓값과 정확히 같으면(page 2147483647 · size 1) 저장소에서 조회한다")
        void offsetExactlyMaxIsQueried() {
            PageRequest pageable = PageRequest.of(Integer.MAX_VALUE, 1);
            when(orderRepository.findAllByOrderByIdDesc(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 3));

            Page<AdminOrderSummary> page = orderQueries.getAllOrders(Integer.MAX_VALUE, 1);

            assertThat(page.getContent()).isEqualTo(List.of());
            assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE);
            verify(orderRepository, never()).count();
        }

        @Test
        @DisplayName("offset이 int 최댓값보다 1 크면(page 1073741824 · size 2) 조회 없이 빈 페이지, 전체 건수는 실제 값")
        void offsetOverMaxIsEmptyWithoutQuery() {
            when(orderRepository.count()).thenReturn(3L);

            Page<AdminOrderSummary> page = orderQueries.getAllOrders(1_073_741_824, 2);

            assertThat(page.getContent()).isEqualTo(List.of());
            assertThat(page.getNumber()).isEqualTo(1_073_741_824);
            assertThat(page.getTotalElements()).isEqualTo(3L);
            assertThat(page.hasNext()).isFalse();
            verify(orderRepository, never()).findAllByOrderByIdDesc(any());
        }
    }

    @Nested
    @DisplayName("주문 처리 타임라인 (#352)")
    class GetTimeline {

        @Test
        @DisplayName("본인 주문을 주문상품과 함께 읽고 배송 건 · 판매자 상호명으로 타임라인을 만든다")
        void buildsTimeline() {
            Order order = withId(withItemIds(placedOrderWithTwoItems()), ORDER_ID);
            when(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));
            when(deliveryRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .thenReturn(List.of(Delivery.create(ORDER_ID, SELLER_ID)));
            when(sellerReplicaRepository.findAllBySellerIdIn(Set.of(SELLER_ID)))
                .thenReturn(List.of(ReplicaSeller.create(
                    new ReplicaSeller.Values(SELLER_ID, "키들리상점", "ACTIVE", OCCURRED_AT))));

            OrderTimeline timeline = orderQueries.getTimeline(BUYER_ID, ORDER_ID);

            assertThat(timeline.orderId()).isEqualTo(ORDER_ID);
            OrderTimeline.Step shipped = timeline.steps().stream()
                .filter(step -> step.step() == OrderTimeline.StepName.SHIPPED).findFirst().orElseThrow();
            assertThat(shipped.deliveries()).singleElement()
                .isEqualTo(new OrderTimeline.DeliveryDetail(null, SELLER_ID, "키들리상점",
                    OrderTimeline.StepStatus.WAITING, null));
        }

        @Test
        @DisplayName("없거나 남의 주문이면 ORDER_NOT_FOUND, 배송 건은 읽지 않는다")
        void failsWhenNotOwned() {
            assertThatThrownBy(() -> orderQueries.getTimeline(BUYER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
            verify(orderRepository).findWithItemsByIdAndMemberId(ORDER_ID, BUYER_ID);
            verify(deliveryRepository, never()).findAllByOrderIdOrderByIdAsc(any());
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("상세에 판매자별 배송을 함께 담는다")
        void includesDeliveries() {
            when(orderRepository.findByIdAndMemberId(ORDER_ID, BUYER_ID))
                .thenReturn(Optional.of(placedOrder(BUYER_ID)));
            when(deliveryRepository.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .thenReturn(List.of(Delivery.create(ORDER_ID, SELLER_ID)));

            OrderDetail detail = orderQueries.getOrder(BUYER_ID, ORDER_ID);

            assertThat(detail.deliveries()).hasSize(1);
            assertThat(detail.deliveries().get(0).sellerId()).isEqualTo(SELLER_ID);
            assertThat(detail.deliveries().get(0).status()).isEqualTo("READY");
        }

        @Test
        @DisplayName("본인 주문의 상세를 돌려준다")
        void returnsOwnOrder() {
            when(orderRepository.findByIdAndMemberId(ORDER_ID, BUYER_ID))
                .thenReturn(Optional.of(placedOrder(BUYER_ID)));

            OrderDetail detail = orderQueries.getOrder(BUYER_ID, ORDER_ID);

            assertThat(detail.totalAmount()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("없거나 남의 주문이면 ORDER_NOT_FOUND")
        void failsWhenNotOwned() {
            assertThatThrownBy(() -> orderQueries.getOrder(BUYER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
            verify(orderRepository).findByIdAndMemberId(ORDER_ID, BUYER_ID);
        }

        @Test
        @DisplayName("목록 offset(page × size)이 int 최댓값과 정확히 같으면(page 2147483647 · size 1) 저장소에서 조회한다")
        void offsetExactlyMaxIsQueried() {
            PageRequest pageable = PageRequest.of(Integer.MAX_VALUE, 1);
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 3));

            Page<OrderSummary> page = orderQueries.getOrders(BUYER_ID, Integer.MAX_VALUE, 1);

            assertThat(page.getContent()).isEqualTo(List.of());
            assertThat(page.getNumber()).isEqualTo(Integer.MAX_VALUE);
            verify(orderRepository, never()).countByMemberId(any());
        }

        @Test
        @DisplayName("목록 offset이 int 최댓값보다 1 크면(page 1073741824 · size 2) 조회 없이 빈 페이지, 전체 건수는 실제 값")
        void offsetOverMaxIsEmptyWithoutQuery() {
            when(orderRepository.countByMemberId(BUYER_ID)).thenReturn(3L);

            Page<OrderSummary> page = orderQueries.getOrders(BUYER_ID, 1_073_741_824, 2);

            assertThat(page.getContent()).isEqualTo(List.of());
            assertThat(page.getNumber()).isEqualTo(1_073_741_824);
            assertThat(page.getSize()).isEqualTo(2);
            assertThat(page.getTotalElements()).isEqualTo(3L);
            assertThat(page.hasNext()).isFalse();
            verify(orderRepository, never()).findAllByMemberIdOrderByIdDesc(any(), any());
        }

        @Test
        @DisplayName("목록은 회원의 주문을 최근 주문부터 요청한 페이지로, 주문상품을 한 번에 읽어 대표 상품명 · 주문상품 수와 함께 돌려준다")
        void returnsPage() {
            Order order = withId(placedOrder(BUYER_ID), ORDER_ID);
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));
            when(orderRepository.findAllWithItemsByIdIn(List.of(ORDER_ID))).thenReturn(List.of(order));

            Page<OrderSummary> page = orderQueries.getOrders(BUYER_ID, 0, 20);

            assertThat(page.getContent()).hasSize(1);
            OrderSummary summary = page.getContent().get(0);
            assertThat(summary.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(summary.totalAmount()).isEqualTo(50_000L);
            assertThat(summary.representativeProductName()).isEqualTo("상품 A");
            assertThat(summary.itemCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("페이지에는 있는데 주문상품 조회에 없는 주문은 예외 없이 대표 상품명 null · 주문상품 수 0, WARN 한 줄")
        void missingItemsAreBlank(CapturedOutput output) {
            Order found = withId(placedOrder(BUYER_ID), ORDER_ID);
            Order missing = withId(placedOrder(BUYER_ID), 8L);
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(missing, found), PageRequest.of(0, 20), 2));
            when(orderRepository.findAllWithItemsByIdIn(List.of(8L, ORDER_ID))).thenReturn(List.of(found));

            Page<OrderSummary> page = orderQueries.getOrders(BUYER_ID, 0, 20);

            assertThat(page.getContent()).extracting(OrderSummary::orderId).containsExactly(8L, ORDER_ID);
            assertThat(page.getContent().get(0).representativeProductName()).isNull();
            assertThat(page.getContent().get(0).itemCount()).isEqualTo(0);
            assertThat(page.getContent().get(1).representativeProductName()).isEqualTo("상품 A");
            assertThat(output).containsPattern("WARN.*reason=order-items-not-found orderId=8");
        }

        @Test
        @DisplayName("빈 페이지면 주문상품을 조회하지 않는다")
        void emptyPageSkipsItems() {
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            assertThat(orderQueries.getOrders(BUYER_ID, 0, 20).getContent()).isEqualTo(List.of());
            verify(orderRepository, never()).findAllWithItemsByIdIn(any());
        }
    }

    private static Order withItemIds(Order order) {
        try {
            java.lang.reflect.Field field = OrderItem.class.getDeclaredField("id");
            field.setAccessible(true);
            long next = 1;
            for (OrderItem item : order.getItems()) {
                field.set(item, next++);
            }
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Order withId(Order order, Long id) {
        try {
            java.lang.reflect.Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
            return order;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Order placedOrderWithTwoItems() {
        OrderItem.ProductSnapshot first =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", null, "110", 25_000L, 1L);
        OrderItem.ProductSnapshot second =
            new OrderItem.ProductSnapshot(2L, 3L, SELLER_ID, "상품 B", null, "120", 30_000L, 2L);
        return Order.place(BUYER_ID, List.of(
                new Order.RequestedItem(first, true, null, 2, 25_000L),
                new Order.RequestedItem(second, true, null, 1, 30_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    private static Order placedOrder(Long memberId) {
        OrderItem.ProductSnapshot product =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", null, "110", 25_000L, 1L);
        return Order.place(memberId, List.of(new Order.RequestedItem(product, true, null, 2, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.of(2026, 10, 14, 9, 0));
    }

}
