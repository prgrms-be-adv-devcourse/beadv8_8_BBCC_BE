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

import com.bbcc.kidly.boundedcontext.order.app.order.dto.AutoConfirmTarget;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.MarkPaidCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderSummary;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.DeliveryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
    private DeliveryRepository deliveryRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<DomainEvent> published;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productReplicaRepository, childReplicaRepository,
            deliveryRepository, eventPublisher, POLICY);
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
    @DisplayName("주문 취소 (결제 실패 · 결제 마감 만료)")
    class Cancel {

        @Test
        @DisplayName("결제 실패면 결제대기 주문을 PAYMENT_FAILED로 취소하고 order.cancelled를 주문상품 순서대로 발행한다")
        void cancelsForPaymentFailure() {
            Order order = placedOrderWithTwoItems();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.cancelForPaymentFailure(ORDER_ID);

            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_FAILED);
            verify(eventPublisher).publish(published.capture());
            OrderCancelledEvent cancelled = (OrderCancelledEvent) published.getValue();
            assertThat(cancelled.data().memberId()).isEqualTo(BUYER_ID);
            assertThat(cancelled.data().reason()).isEqualTo("PAYMENT_FAILED");
            assertThat(cancelled.data().items()).hasSize(2);
            assertThat(cancelled.data().items().get(0).optionId()).isEqualTo(OPTION_ID);
            assertThat(cancelled.data().items().get(0).quantity()).isEqualTo(2);
            assertThat(cancelled.data().items().get(1).optionId()).isEqualTo(3L);
            assertThat(cancelled.data().items().get(1).quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("결제 마감이 지난 주문이면 PAYMENT_EXPIRED로 취소하고 order.cancelled를 발행한다")
        void cancelsForPaymentExpiry() {
            Order order = placedOrderAt(LocalDateTime.now().minusDays(1));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.cancelForPaymentExpiry(ORDER_ID);

            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
            verify(eventPublisher).publish(published.capture());
            assertThat(((OrderCancelledEvent) published.getValue()).data().reason()).isEqualTo("PAYMENT_EXPIRED");
        }

        @Test
        @DisplayName("결제 마감 전 주문이면 만료 취소하지 않고 발행하지 않는다")
        void doesNotExpireBeforeDue() {
            Order order = placedOrderAt(LocalDateTime.now());
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.cancelForPaymentExpiry(ORDER_ID);

            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("결제완료 주문은 취소하지 않고 발행하지 않는다")
        void doesNotCancelPaidOrder() {
            Order order = placedOrder(BUYER_ID);
            pay(order, 500L, LocalDateTime.of(2026, 10, 14, 9, 1));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.cancelForPaymentExpiry(ORDER_ID);

            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("주문이 없으면 아무것도 하지 않는다")
        void ignoresMissingOrder() {
            orderService.cancelForPaymentFailure(ORDER_ID);

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("만료 대상은 결제대기 · 받은 기준 시각 · 커서 · 건수로 저장소에 묻고, 저장소가 준 ID 순서 그대로 돌려준다")
        void findsOverdueOrderIds() {
            LocalDateTime now = LocalDateTime.of(2026, 10, 15, 9, 0);
            when(orderRepository.findOverduePaymentOrderIds(Order.Status.PENDING_PAYMENT, now, 2L, Limit.of(50)))
                .thenReturn(List.of(3L, 5L));

            assertThat(orderService.findOverduePaymentOrderIds(now, 2L, 50)).isEqualTo(List.of(3L, 5L));
        }
    }

    @Nested
    @DisplayName("구매확정 (직접 · 자동)")
    class Confirm {

        private static final Long ORDER_ITEM_ID = 1L;

        @Test
        @DisplayName("직접 확정하면 order-item.purchase-confirmed를 발행하고 확정된 주문상품을 돌려준다")
        void confirmsByBuyer() {
            Order order = deliveredOrder(LocalDateTime.now().minusDays(1));
            when(orderRepository.findByIdAndMemberId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

            OrderDetail.Item item = orderService.confirmByBuyer(BUYER_ID, ORDER_ID, ORDER_ITEM_ID);

            assertThat(item.orderItemId()).isEqualTo(ORDER_ITEM_ID);
            assertThat(item.status()).isEqualTo("CONFIRMED");
            assertThat(item.confirmType()).isEqualTo("DIRECT");
            assertThat(item.confirmedAt()).isNotNull();
            verify(eventPublisher).publish(published.capture());
            OrderItemPurchaseConfirmedEvent.Data data = ((OrderItemPurchaseConfirmedEvent) published.getValue()).data();
            assertThat(data.orderItemId()).isEqualTo(ORDER_ITEM_ID);
            assertThat(data.memberId()).isEqualTo(BUYER_ID);
            assertThat(data.sellerId()).isEqualTo(SELLER_ID);
            assertThat(data.childId()).isNull();
            assertThat(data.productId()).isEqualTo(1L);
            assertThat(data.optionId()).isEqualTo(OPTION_ID);
            assertThat(data.size()).isEqualTo("110");
            assertThat(data.quantity()).isEqualTo(2);
            assertThat(data.amount()).isEqualTo(50_000L);
            assertThat(data.confirmedAt()).isEqualTo(item.confirmedAt());
        }

        @Test
        @DisplayName("이미 확정이면 발행하지 않고 주문상품은 그대로 돌려준다")
        void alreadyConfirmed() {
            Order order = deliveredOrder(LocalDateTime.now().minusDays(1));
            order.confirmItem(ORDER_ITEM_ID, LocalDateTime.now().minusHours(1));
            when(orderRepository.findByIdAndMemberId(ORDER_ID, BUYER_ID)).thenReturn(Optional.of(order));

            OrderDetail.Item item = orderService.confirmByBuyer(BUYER_ID, ORDER_ID, ORDER_ITEM_ID);

            assertThat(item.status()).isEqualTo("CONFIRMED");
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("없거나 남의 주문이면 ORDER_NOT_FOUND")
        void othersOrder() {
            assertThatThrownBy(() -> orderService.confirmByBuyer(BUYER_ID, ORDER_ID, ORDER_ITEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
            verify(orderRepository).findByIdAndMemberId(ORDER_ID, BUYER_ID);
        }

        @Test
        @DisplayName("자동 확정: 배송 완료 후 기간이 지났으면 AUTO로 확정하고 발행한다")
        void autoConfirmsAfterPeriod() {
            Order order = deliveredOrder(LocalDateTime.now().minus(POLICY.autoConfirmAfter()).minusMinutes(1));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.confirmAutomatically(ORDER_ID, ORDER_ITEM_ID);

            assertThat(order.getItems().get(0).getConfirmType()).isEqualTo(OrderItem.ConfirmType.AUTO);
            verify(eventPublisher).publish(published.capture());
            assertThat(((OrderItemPurchaseConfirmedEvent) published.getValue()).data().orderItemId())
                .isEqualTo(ORDER_ITEM_ID);
        }

        @Test
        @DisplayName("자동 확정: 기간 전이면 확정하지 않고 발행하지 않는다")
        void doesNotAutoConfirmBeforePeriod() {
            Order order = deliveredOrder(LocalDateTime.now().minus(POLICY.autoConfirmAfter()).plusMinutes(1));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.confirmAutomatically(ORDER_ID, ORDER_ITEM_ID);

            assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItem.Status.ORDERED);
            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("자동 확정: 주문이 없으면 아무것도 하지 않는다")
        void autoIgnoresMissingOrder() {
            orderService.confirmAutomatically(ORDER_ID, ORDER_ITEM_ID);

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("자동 확정 대상은 받은 기준 시각 − 기간 · 주문됨 · 커서 · 건수로 좁혀 (주문 ID, 주문상품 ID)로 돌려준다")
        void findsAutoConfirmTargets() {
            LocalDateTime now = LocalDateTime.of(2026, 10, 15, 9, 0);
            when(orderRepository.findAutoConfirmTargets(OrderItem.Status.ORDERED, now.minus(POLICY.autoConfirmAfter()),
                60L, Limit.of(50)))
                .thenReturn(List.of(row(7L, 70L), row(8L, 80L)));

            List<AutoConfirmTarget> targets = orderService.findAutoConfirmTargets(now, 60L, 50);

            assertThat(targets).isEqualTo(List.of(new AutoConfirmTarget(7L, 70L), new AutoConfirmTarget(8L, 80L)));
        }

        /**
         * 결제완료 후 deliveredAt에 판매자 몫 배송 완료된 주문. 주문상품 ID는 1.
         */
        private Order deliveredOrder(LocalDateTime deliveredAt) {
            Order order = placedOrderAt(deliveredAt.minusDays(2));
            pay(order, 500L, deliveredAt.minusDays(1));
            order.recordDelivered(SELLER_ID, deliveredAt);
            setItemId(order.getItems().get(0), ORDER_ITEM_ID);
            return order;
        }

        private OrderRepository.AutoConfirmRow row(Long orderId, Long orderItemId) {
            return new OrderRepository.AutoConfirmRow() {
                @Override
                public Long getOrderId() {
                    return orderId;
                }

                @Override
                public Long getOrderItemId() {
                    return orderItemId;
                }
            };
        }
    }

    @Nested
    @DisplayName("결제 완료 (payment.completed)")
    class MarkPaid {

        @Test
        @DisplayName("결제대기 주문을 결제완료로 바꾸고 true")
        void marksPaid() {
            Order order = placedOrder(BUYER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            boolean paid = orderService.markPaid(commandFor(order, 500L));

            assertThat(paid).isTrue();
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getPaymentId()).isEqualTo(500L);
            assertThat(order.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 결제완료면 그대로 두고 false (중복 이벤트)")
        void duplicateIsFalse() {
            Order order = placedOrder(BUYER_ID);
            pay(order, 500L, LocalDateTime.of(2026, 10, 14, 9, 1));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThat(orderService.markPaid(commandFor(order, 999L))).isFalse();
            assertThat(order.getPaymentId()).isEqualTo(500L);
        }

        @Test
        @DisplayName("결제대기인데 회원이 다르면 주문의 결제 불일치 예외가 그대로 나가고 결제대기로 남는다")
        void mismatchPropagates() {
            Order order = placedOrder(BUYER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            MarkPaidCommand matching = commandFor(order, 500L);

            assertThatThrownBy(() -> orderService.markPaid(new MarkPaidCommand(ORDER_ID, 500L, 999L,
                matching.amount(), matching.items())))
                .isInstanceOf(PaymentMismatchException.class)
                .hasMessageContaining("reason=member-mismatch");
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("주문상품 목록이 비어 있으면(null) NPE 없이 item-missing 예외")
        void missingItemsAreItemMissing() {
            Order order = placedOrder(BUYER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            withItemIds(order);

            assertThatThrownBy(() -> orderService.markPaid(
                new MarkPaidCommand(ORDER_ID, 500L, BUYER_ID, order.getTotalAmount(), null)))
                .isInstanceOf(PaymentMismatchException.class)
                .hasMessageContaining("reason=item-missing");
        }

        @Test
        @DisplayName("주문이 없으면 false")
        void missingOrderIsFalse() {
            assertThat(orderService.markPaid(new MarkPaidCommand(ORDER_ID, 500L, BUYER_ID, 50_000L, List.of())))
                .isFalse();
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

            OrderDetail detail = orderService.getOrder(BUYER_ID, ORDER_ID);

            assertThat(detail.deliveries()).hasSize(1);
            assertThat(detail.deliveries().get(0).sellerId()).isEqualTo(SELLER_ID);
            assertThat(detail.deliveries().get(0).status()).isEqualTo("READY");
        }

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
        @DisplayName("목록은 회원의 주문을 최근 주문부터 요청한 페이지로, 주문상품을 한 번에 읽어 대표 상품명 · 주문상품 수와 함께 돌려준다")
        void returnsPage() {
            Order order = withId(placedOrder(BUYER_ID), ORDER_ID);
            when(orderRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));
            when(orderRepository.findAllWithItemsByIdIn(List.of(ORDER_ID))).thenReturn(List.of(order));

            Page<OrderSummary> page = orderService.getOrders(BUYER_ID, 0, 20);

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

            Page<OrderSummary> page = orderService.getOrders(BUYER_ID, 0, 20);

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

            assertThat(orderService.getOrders(BUYER_ID, 0, 20).getContent()).isEqualTo(List.of());
            verify(orderRepository, never()).findAllWithItemsByIdIn(any());
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

    private static void setItemId(OrderItem item, Long id) {
        try {
            java.lang.reflect.Field field = OrderItem.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(item, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 주문과 그대로 맞는 결제 완료(주문한 회원 · 총액 · 주문상품별 금액)를 도메인에 바로 반영한다.
     */
    private static void pay(Order order, Long paymentId, LocalDateTime paidAt) {
        MarkPaidCommand command = commandFor(order, paymentId);
        order.markPaid(paymentId, command.memberId(), command.amount(), command.items().stream()
            .map(item -> new Order.PaidItem(item.orderItemId(), item.amount()))
            .toList(), paidAt);
    }

    /**
     * 주문과 그대로 맞는 결제 완료 입력. 주문상품에 ID가 없으면 먼저 넣는다(결제 완료는 주문상품 ID로 대조한다).
     */
    private static MarkPaidCommand commandFor(Order order, Long paymentId) {
        withItemIds(order);
        return new MarkPaidCommand(ORDER_ID, paymentId, order.getMemberId(), order.getTotalAmount(),
            order.getItems().stream()
                .map(item -> new MarkPaidCommand.Item(item.getId(), item.getAmount()))
                .toList());
    }

    /**
     * 저장 전 주문상품에는 ID가 없어서, 주문한 순서대로 1, 2, … ID를 넣는다.
     */
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

    /**
     * 서비스가 지금 시각과 비교하는 테스트용. 주문 시각은 실행 시각 기준 상대값으로 준다(시간대와 무관하게).
     */
    private static Order placedOrderAt(LocalDateTime orderedAt) {
        OrderItem.ProductSnapshot product =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", null, "110", 25_000L, 1L);
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(product, true, null, 2, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY, orderedAt);
    }

    private static Order placedOrder(Long memberId) {
        OrderItem.ProductSnapshot product =
            new OrderItem.ProductSnapshot(1L, OPTION_ID, SELLER_ID, "상품 A", null, "110", 25_000L, 1L);
        return Order.place(memberId, List.of(new Order.RequestedItem(product, true, null, 2, 25_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY,
            LocalDateTime.of(2026, 10, 14, 9, 0));
    }
}
