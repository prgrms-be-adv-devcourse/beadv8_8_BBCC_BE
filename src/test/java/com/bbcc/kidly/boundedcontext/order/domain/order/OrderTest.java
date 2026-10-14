package com.bbcc.kidly.boundedcontext.order.domain.order;

import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.ADDRESS;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.CHILD_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.DELIVERED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.NOW;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.PAID_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.PAYMENT_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.PAYMENT_TIMEOUT;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.POLICY;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_A_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_B_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.child;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.itemOf;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.line;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.productOfSellerA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Order 상태 전이 규칙(주문 정리 5.1 · 5.2).
 * 이벤트로 오는 전이(결제 요청 · 결제 완료 · 취소 · 만료 · 배송 완료 기록)는 상태가 맞지 않으면 "반영하지 않는다"만 검사한다.
 * 예외로 알릴지 무시할지는 도메인 구현이 정한다(열린 질문 9).
 */
class OrderTest {

    @Nested
    @DisplayName("주문하기")
    class Place {

        @Test
        @DisplayName("결제대기로 저장되고 결제 마감은 주문 시각 + 결제 제한 시간이다")
        void placeAsPendingPayment() {
            Order order = OrderFixture.placedOrder();

            assertThat(order.getMemberId()).isEqualTo(BUYER_ID);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
            assertThat(order.getCancelReason()).isNull();
            assertThat(order.getPaymentDueAt()).isEqualTo(NOW.plus(PAYMENT_TIMEOUT));
            assertThat(order.getPaymentRequestedAt()).isNull();
            assertThat(order.getPaidAt()).isNull();
            assertThat(order.getPaymentId()).isNull();
            assertThat(order.getShippingAddress()).isEqualTo(ADDRESS);
        }

        @Test
        @DisplayName("주문상품은 옵션 × 자녀 한 줄마다 하나씩 주문됨 상태로 만들어진다")
        void itemsPerLine() {
            Order order = OrderFixture.placedOrder();

            assertThat(order.getItems()).hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.getStatus()).isEqualTo(OrderItem.Status.ORDERED);
                    assertThat(item.getDeliveredAt()).isNull();
                    assertThat(item.getConfirmedAt()).isNull();
                });
        }

        @Test
        @DisplayName("같은 옵션이라도 자녀가 다르면 다른 주문상품이다")
        void sameOptionDifferentChildIsSeparateItem() {
            Order order = OrderFixture.place(List.of(
                line(productOfSellerA(), child(), 1),
                line(productOfSellerA(), null, 1)));

            assertThat(order.getItems()).hasSize(2);
        }

        @Test
        @DisplayName("주문상품 금액 = 단가 × 수량, 총액은 주문상품 금액의 합이다")
        void amounts() {
            Order order = OrderFixture.placedOrder();

            OrderItem itemA = itemOf(order, SELLER_A_ID);
            assertThat(itemA.getQuantity()).isEqualTo(2);
            assertThat(itemA.getAmount()).isEqualTo(38_000L);
            assertThat(itemOf(order, SELLER_B_ID).getAmount()).isEqualTo(15_000L);
            assertThat(order.getTotalAmount()).isEqualTo(53_000L);
        }

        @Test
        @DisplayName("주문상품마다 주문 당시 상품 정보를 스냅샷으로 남긴다")
        void productSnapshot() {
            OrderItem.ProductSnapshot snapshot = itemOf(OrderFixture.placedOrder(), SELLER_A_ID).getProduct();

            assertThat(snapshot.productId()).isEqualTo(1L);
            assertThat(snapshot.optionId()).isEqualTo(1001L);
            assertThat(snapshot.sellerId()).isEqualTo(SELLER_A_ID);
            assertThat(snapshot.productName()).isEqualTo("데일리 맨투맨");
            assertThat(snapshot.size()).isEqualTo("110");
            assertThat(snapshot.unitPrice()).isEqualTo(19_000L);
            assertThat(snapshot.categoryId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("자녀를 고른 주문상품만 자녀 스냅샷을 남긴다")
        void childSnapshotOnlyWhenChildSelected() {
            Order order = OrderFixture.placedOrder();

            OrderItem.ChildSnapshot snapshot = itemOf(order, SELLER_A_ID).getChild();
            assertThat(snapshot.childId()).isEqualTo(CHILD_ID);
            assertThat(snapshot.name()).isEqualTo("김아이");
            assertThat(snapshot.heightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
            assertThat(snapshot.weightKg()).isEqualByComparingTo(new BigDecimal("20.0"));
            assertThat(snapshot.bodyType()).isEqualTo("NORMAL");
            assertThat(itemOf(order, SELLER_B_ID).getChild()).isNull();
        }

        @Test
        @DisplayName("주문상품 없이는 주문할 수 없다")
        void requiresLines() {
            assertThatThrownBy(() -> OrderFixture.place(List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }

        @Test
        @DisplayName("수량은 1 이상이어야 한다")
        void requiresPositiveQuantity() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(line(productOfSellerA(), null, 0))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("화면 단가가 현재 단가와 다르면 주문할 수 없다 (409)")
        void priceChanged() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                OrderFixture.lineWithRequestedPrice(productOfSellerA(), 18_000L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.PRICE_CHANGED);
        }

        @Test
        @DisplayName("판매 중이 아닌 상품은 주문할 수 없다 (409)")
        void notPurchasable() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(OrderFixture.notPurchasableProduct(), null, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.PRODUCT_NOT_PURCHASABLE);
        }

        @Test
        @DisplayName("판매자는 자기 상품을 주문할 수 없다 (403)")
        void ownProduct() {
            assertThatThrownBy(() -> Order.place(SELLER_A_ID, List.of(line(productOfSellerA(), null, 1)),
                ADDRESS, POLICY, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.OWN_PRODUCT);
        }

        @Test
        @DisplayName("다른 회원의 자녀로는 주문할 수 없다 (409, 임시)")
        void otherMembersChild() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(OTHER_MEMBER_ID, false), 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.CHILD_NOT_ORDERABLE);
        }

        @Test
        @DisplayName("삭제된 자녀로는 주문할 수 없다 (409, 임시)")
        void deletedChild() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(BUYER_ID, true), 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.CHILD_NOT_ORDERABLE);
        }

        @Test
        @DisplayName("같은 옵션 · 같은 자녀 주문 요청 항목이 두 번 있으면 주문할 수 없다 (400)")
        void duplicateItem() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(), 1),
                line(productOfSellerA(), child(), 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OrderErrorCode.DUPLICATE_ORDER_ITEM);
        }
    }

    @Nested
    @DisplayName("결제 요청 (stock.deducted)")
    class RequestPayment {

        @Test
        @DisplayName("결제대기이고 요청 전이면 결제 요청 시각을 기록한다")
        void recordsRequestedAt() {
            Order order = OrderFixture.placedOrder();

            boolean requested = order.requestPayment(NOW.plusSeconds(5));

            assertThat(requested).isTrue();
            assertThat(order.getPaymentRequestedAt()).isEqualTo(NOW.plusSeconds(5));
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("이미 요청했으면 처음 시각을 유지한다 (중복 이벤트)")
        void keepsFirstRequest() {
            Order order = OrderFixture.placedOrder();
            order.requestPayment(NOW.plusSeconds(5));

            boolean requested = order.requestPayment(NOW.plusSeconds(9));

            assertThat(requested).isFalse();
            assertThat(order.getPaymentRequestedAt()).isEqualTo(NOW.plusSeconds(5));
        }

        @Test
        @DisplayName("취소된 주문은 결제를 요청하지 않는다")
        void notWhenCancelled() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            boolean requested = order.requestPayment(NOW.plusMinutes(12));

            assertThat(requested).isFalse();
            assertThat(order.getPaymentRequestedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("결제 완료 (payment.completed)")
    class MarkPaid {

        @Test
        @DisplayName("결제대기 주문은 결제완료가 되고 결제 ID와 시각을 남긴다")
        void markPaid() {
            Order order = OrderFixture.placedOrder();

            boolean paid = order.markPaid(PAYMENT_ID, PAID_AT);

            assertThat(paid).isTrue();
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("이미 취소된 주문은 결제완료가 되지 않는다 (먼저 바꾼 쪽이 이김)")
        void cancelledWins() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            boolean paid = order.markPaid(PAYMENT_ID, NOW.plusMinutes(12));

            assertThat(paid).isFalse();
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getPaymentId()).isNull();
            assertThat(order.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("같은 결제 완료가 두 번 와도 처음 값을 유지한다")
        void duplicateKeepsFirst() {
            Order order = OrderFixture.paidOrder();

            boolean paid = order.markPaid(999L, PAID_AT.plusMinutes(1));

            assertThat(paid).isFalse();
            assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("결제대기 주문은 사유와 함께 취소된다")
        void cancelPending() {
            Order order = OrderFixture.placedOrder();

            boolean cancelled = order.cancel(Order.CancelReason.OUT_OF_STOCK);

            assertThat(cancelled).isTrue();
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("결제완료 주문은 취소되지 않는다 (결제 후 취소 없음)")
        void paidIsNotCancelled() {
            Order order = OrderFixture.paidOrder();

            boolean cancelled = order.cancel(Order.CancelReason.PAYMENT_FAILED);

            assertThat(cancelled).isFalse();
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("이미 취소된 주문은 처음 사유를 유지한다")
        void keepsFirstReason() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.OUT_OF_STOCK);

            boolean cancelled = order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(cancelled).isFalse();
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        }
    }

    @Nested
    @DisplayName("배송 완료 기록 (delivery.completed)")
    class RecordDelivered {

        @Test
        @DisplayName("그 판매자 몫 주문상품에만 배송 완료 시각을 기록한다")
        void onlySellersItems() {
            Order order = OrderFixture.deliveredToSellerA();

            assertThat(itemOf(order, SELLER_A_ID).getDeliveredAt()).isEqualTo(DELIVERED_AT);
            assertThat(itemOf(order, SELLER_B_ID).getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("같은 배송 완료가 두 번 와도 처음 시각을 유지한다")
        void duplicateKeepsFirst() {
            Order order = OrderFixture.deliveredToSellerA();

            catchThrowable(() -> order.recordDelivered(SELLER_A_ID, DELIVERED_AT.plusHours(1)));

            assertThat(itemOf(order, SELLER_A_ID).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        }
    }
}
