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

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderPaymentRequestedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final Long OPTION_ID = 1L;
    private static final Long CHILD_ID = 1L;
    private static final Long ORDER_ID = 7L;
    private static final PlaceOrderCommand.ShippingAddress ADDRESS =
        new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductReplicaRepository productReplicaRepository;

    @Mock
    private ChildReplicaRepository childReplicaRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<DomainEvent> published;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productReplicaRepository, childReplicaRepository,
            eventPublisher, POLICY);
    }

    @Nested
    @DisplayName("주문하기")
    class Place {

        @Test
        @DisplayName("주문을 저장하고 order.created를 발행한 뒤 상세를 돌려준다")
        void placesOrder() {
            givenProduct();
            givenChild();
            when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            OrderDetail detail = orderService.place(BUYER_ID, command(25_000L));

            verify(orderRepository).save(any());
            verify(eventPublisher).publish(published.capture());
            OrderCreatedEvent created = (OrderCreatedEvent) published.getValue();
            assertThat(created.data().memberId()).isEqualTo(BUYER_ID);
            assertThat(created.data().items()).hasSize(1);
            assertThat(created.data().items().get(0).optionId()).isEqualTo(OPTION_ID);
            assertThat(created.data().items().get(0).quantity()).isEqualTo(2);

            assertThat(detail.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(detail.totalAmount()).isEqualTo(50_000L);
            assertThat(detail.shippingAddress().recipientName()).isEqualTo("홍길동");
            assertThat(detail.items()).hasSize(1);
            OrderDetail.Item item = detail.items().get(0);
            assertThat(item.productName()).isEqualTo("상품 A");
            assertThat(item.sellerId()).isEqualTo(SELLER_ID);
            assertThat(item.amount()).isEqualTo(50_000L);
            assertThat(item.child().childId()).isEqualTo(CHILD_ID);
            assertThat(item.child().heightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
        }

        @Test
        @DisplayName("상품 옵션 복사본이 없으면 PRODUCT_OPTION_NOT_FOUND, 저장 · 발행하지 않는다")
        void failsWhenOptionMissing() {
            assertPlaceFails(command(25_000L), OrderErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        @Test
        @DisplayName("자녀 복사본이 없으면 CHILD_NOT_FOUND, 저장 · 발행하지 않는다")
        void failsWhenChildMissing() {
            givenProduct();

            assertPlaceFails(command(25_000L), OrderErrorCode.CHILD_NOT_FOUND);
        }

        @Test
        @DisplayName("화면 단가가 지금 단가와 다르면 도메인 검사로 PRICE_CHANGED, 저장 · 발행하지 않는다")
        void failsWhenPriceChanged() {
            givenProduct();
            givenChild();

            assertPlaceFails(command(23_000L), OrderErrorCode.PRICE_CHANGED);
        }

        private void assertPlaceFails(PlaceOrderCommand command, OrderErrorCode expected) {
            assertThatThrownBy(() -> orderService.place(BUYER_ID, command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("결제 요청 (stock.deducted)")
    class RequestPayment {

        @Test
        @DisplayName("결제대기이고 요청 전이면 order.payment-requested를 주문상품별 금액과 주문 총액으로 발행한다")
        void publishesPaymentRequested() {
            Order order = placedOrderWithTwoItems();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.requestPayment(ORDER_ID);

            assertThat(order.getPaymentRequestedAt()).isNotNull();
            verify(eventPublisher).publish(published.capture());
            OrderPaymentRequestedEvent requested = (OrderPaymentRequestedEvent) published.getValue();
            assertThat(requested.data().memberId()).isEqualTo(BUYER_ID);
            assertThat(requested.data().items()).hasSize(2);
            assertThat(requested.data().items().get(0).amount()).isEqualTo(50_000L);
            assertThat(requested.data().items().get(1).amount()).isEqualTo(30_000L);
            assertThat(requested.data().amount()).isEqualTo(80_000L);
            assertThat(requested.data().amount()).isEqualTo(requested.data().items().stream()
                .mapToLong(OrderPaymentRequestedEvent.Item::amount).sum());
        }

        @Test
        @DisplayName("이미 결제를 요청한 주문이면 다시 발행하지 않는다 (중복 이벤트)")
        void doesNotPublishTwice() {
            Order order = placedOrder(BUYER_ID);
            order.requestPayment(OCCURRED_AT);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.requestPayment(ORDER_ID);

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("주문이 없으면 아무것도 하지 않는다")
        void ignoresMissingOrder() {
            orderService.requestPayment(ORDER_ID);

            verify(eventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("본인 주문의 상세를 돌려준다")
        void returnsOwnOrder() {
            when(orderRepository.findByIdAndMemberId(ORDER_ID, BUYER_ID))
                .thenReturn(Optional.of(placedOrder(BUYER_ID)));

            OrderDetail detail = orderService.getOrder(BUYER_ID, ORDER_ID);

            assertThat(detail.totalAmount()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("없거나 남의 주문이면 ORDER_NOT_FOUND")
        void failsWhenNotOwned() {
            assertThatThrownBy(() -> orderService.getOrder(BUYER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
            verify(orderRepository).findByIdAndMemberId(ORDER_ID, BUYER_ID);
        }

        @Test
        @DisplayName("목록은 회원의 주문을 최근 주문부터 요청한 페이지로 돌려준다")
        void returnsPage() {
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(placedOrder(BUYER_ID)), PageRequest.of(0, 20), 1));

            Page<OrderSummary> page = orderService.getOrders(BUYER_ID, 0, 20);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).status()).isEqualTo("PENDING_PAYMENT");
            assertThat(page.getContent().get(0).totalAmount()).isEqualTo(50_000L);
        }
    }

    private void givenProduct() {
        ReplicaProduct product = ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT));
        when(productReplicaRepository.findByOptionId(OPTION_ID)).thenReturn(Optional.of(product));
    }

    private void givenChild() {
        ReplicaChild child = ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT));
        when(childReplicaRepository.findByChildId(CHILD_ID)).thenReturn(Optional.of(child));
    }

    private static PlaceOrderCommand command(long unitPrice) {
        return new PlaceOrderCommand(List.of(new PlaceOrderCommand.Item(OPTION_ID, CHILD_ID, 2, unitPrice)), ADDRESS);
    }

    private static Order placedOrderWithTwoItems() {
        OrderItem.ProductSnapshot first =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", "110", 25_000L, 1L);
        OrderItem.ProductSnapshot second =
            new OrderItem.ProductSnapshot(2L, 3L, SELLER_ID, "상품 B", "120", 30_000L, 2L);
        return Order.place(BUYER_ID, List.of(
                new Order.RequestedItem(first, true, null, 2, 25_000L),
                new Order.RequestedItem(second, true, null, 1, 30_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    private static Order placedOrder(Long memberId) {
        OrderItem.ProductSnapshot product =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", "110", 25_000L, 1L);
        return Order.place(memberId, List.of(new Order.RequestedItem(product, true, null, 2, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.of(2026, 10, 14, 9, 0));
    }
}
