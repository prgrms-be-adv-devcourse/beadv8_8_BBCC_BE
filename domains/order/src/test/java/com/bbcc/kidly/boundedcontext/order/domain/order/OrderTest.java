package com.bbcc.kidly.boundedcontext.order.domain.order;

import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.ADDRESS;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.AUTO_CONFIRM_AFTER;
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

import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException;
import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException.Rule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.EMPTY_ORDER_ITEMS);
        }

        @Test
        @DisplayName("수량은 1 이상이어야 한다")
        void requiresPositiveQuantity() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(line(productOfSellerA(), null, 0))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("수량 1은 주문할 수 있다 (최솟값)")
        void allowsMinimumQuantity() {
            Order order = OrderFixture.place(List.of(line(productOfSellerA(), null, 1)));

            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getItems().get(0).getQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("화면 단가가 현재 단가와 같으면 주문된다")
        void samePriceIsPlaced() {
            long current = productOfSellerA().snapshot().unitPrice();

            Order order = OrderFixture.place(List.of(
                OrderFixture.lineWithRequestedPrice(productOfSellerA(), current)));

            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("화면 단가가 현재 단가보다 1원 많거나 적어도 주문할 수 없다 (409)")
        void priceChangedByOneWon() {
            long current = productOfSellerA().snapshot().unitPrice();

            for (long requested : new long[] {current + 1, current - 1}) {
                assertThatThrownBy(() -> OrderFixture.place(List.of(
                    OrderFixture.lineWithRequestedPrice(productOfSellerA(), requested))))
                    .isInstanceOf(OrderRuleViolationException.class)
                    .extracting("rule").isEqualTo(Rule.PRICE_CHANGED);
            }
        }

        @Test
        @DisplayName("화면 단가가 현재 단가와 다르면 주문할 수 없다 (409)")
        void priceChanged() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                OrderFixture.lineWithRequestedPrice(productOfSellerA(), 18_000L))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.PRICE_CHANGED);
        }

        @Test
        @DisplayName("판매 중이 아닌 상품은 주문할 수 없다 (409)")
        void notPurchasable() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(OrderFixture.notPurchasableProduct(), null, 1))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.PRODUCT_NOT_PURCHASABLE);
        }

        @Test
        @DisplayName("판매자는 자기 상품을 주문할 수 없다 (403)")
        void ownProduct() {
            assertThatThrownBy(() -> Order.place(SELLER_A_ID, List.of(line(productOfSellerA(), null, 1)),
                ADDRESS, POLICY, NOW))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.OWN_PRODUCT);
        }

        @Test
        @DisplayName("다른 회원의 자녀로는 주문할 수 없다 (409, 임시)")
        void otherMembersChild() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(OTHER_MEMBER_ID, false), 1))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.CHILD_NOT_ORDERABLE);
        }

        @Test
        @DisplayName("삭제된 자녀로는 주문할 수 없다 (409, 임시)")
        void deletedChild() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(BUYER_ID, true), 1))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.CHILD_NOT_ORDERABLE);
        }

        @Test
        @DisplayName("같은 옵션 · 같은 자녀 주문 요청 항목이 두 번 있으면 주문할 수 없다 (400)")
        void duplicateItem() {
            assertThatThrownBy(() -> OrderFixture.place(List.of(
                line(productOfSellerA(), child(), 1),
                line(productOfSellerA(), child(), 2))))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.DUPLICATE_ORDER_ITEM);
        }
    }

    @Nested
    @DisplayName("결제 요청 (stock.deducted)")
    class RequestPayment {

        @Test
        @DisplayName("결제대기이고 요청 전이면 결제 요청 시각을 기록한다")
        void recordsRequestedAt() {
            Order order = OrderFixture.placedOrder();

            TransitionResult requested = order.requestPayment(NOW.plusSeconds(5));

            assertThat(requested).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getPaymentRequestedAt()).isEqualTo(NOW.plusSeconds(5));
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("이미 요청했으면 처음 시각을 유지한다 (중복 이벤트)")
        void keepsFirstRequest() {
            Order order = OrderFixture.placedOrder();
            order.requestPayment(NOW.plusSeconds(5));

            TransitionResult requested = order.requestPayment(NOW.plusSeconds(9));

            assertThat(requested).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getPaymentRequestedAt()).isEqualTo(NOW.plusSeconds(5));
        }

        @Test
        @DisplayName("취소된 주문은 결제를 요청하지 않는다")
        void notWhenCancelled() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            TransitionResult requested = order.requestPayment(NOW.plusMinutes(12));

            assertThat(requested).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getPaymentRequestedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("결제 마감 만료")
    class ExpirePayment {

        private final LocalDateTime dueAt = NOW.plus(PAYMENT_TIMEOUT);

        @Test
        @DisplayName("마감 1초 전이면 취소하지 않는다")
        void beforeDue() {
            Order order = OrderFixture.placedOrder();

            TransitionResult expired = order.expirePayment(dueAt.minusSeconds(1));

            assertThat(expired).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("정확히 마감 시각이면 PAYMENT_EXPIRED로 취소한다")
        void atDue() {
            Order order = OrderFixture.placedOrder();

            TransitionResult expired = order.expirePayment(dueAt);

            assertThat(expired).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
        }

        @Test
        @DisplayName("마감 1초 뒤면 PAYMENT_EXPIRED로 취소한다")
        void afterDue() {
            Order order = OrderFixture.placedOrder();

            assertThat(order.expirePayment(dueAt.plusSeconds(1))).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
        }

        @Test
        @DisplayName("결제완료 주문은 마감이 지나도 그대로다")
        void paidIsNotExpired() {
            Order order = OrderFixture.paidOrder();

            assertThat(order.expirePayment(dueAt.plusMinutes(1))).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("이미 취소된 주문은 처음 사유를 유지한다")
        void cancelledKeepsFirstReason() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_FAILED);

            assertThat(order.expirePayment(dueAt.plusMinutes(1))).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_FAILED);
        }
    }

    @Nested
    @DisplayName("직접 구매확정")
    class ConfirmItem {

        private final LocalDateTime confirmedAt = DELIVERED_AT.plusDays(1);

        @Test
        @DisplayName("배송 완료된 주문상품을 직접 확정하고 APPLIED")
        void confirmsDelivered() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);

            TransitionResult confirmed = order.confirmItem(item.getId(), confirmedAt);

            assertThat(confirmed).isEqualTo(TransitionResult.APPLIED);
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
            assertThat(item.getConfirmedAt()).isEqualTo(confirmedAt);
        }

        @Test
        @DisplayName("이미 확정이면 그대로 두고 ALREADY_DONE")
        void alreadyConfirmed() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            Long itemId = itemOf(order, SELLER_A_ID).getId();
            order.confirmItem(itemId, confirmedAt);

            assertThat(order.confirmItem(itemId, confirmedAt.plusDays(1))).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(itemOf(order, SELLER_A_ID).getConfirmedAt()).isEqualTo(confirmedAt);
        }

        @Test
        @DisplayName("배송 전이면 NOT_DELIVERED")
        void notDelivered() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            Long itemId = itemOf(order, SELLER_B_ID).getId();

            assertThatThrownBy(() -> order.confirmItem(itemId, confirmedAt))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.NOT_DELIVERED);
        }

        @Test
        @DisplayName("주문에 없는 주문상품이면 ORDER_ITEM_NOT_FOUND")
        void missingItem() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());

            assertThatThrownBy(() -> order.confirmItem(999L, confirmedAt))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.ORDER_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("자동 구매확정 (배송 완료 후 기간이 지나면)")
    class AutoConfirmItem {

        private final LocalDateTime dueAt = DELIVERED_AT.plus(AUTO_CONFIRM_AFTER);

        @Test
        @DisplayName("기간 1초 전이면 확정하지 않는다")
        void beforeDue() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt.minusSeconds(1)))
                .isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.ORDERED);
        }

        @Test
        @DisplayName("정확히 기간이 되면 자동 확정한다")
        void atDue() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt)).isEqualTo(TransitionResult.APPLIED);
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.AUTO);
            assertThat(item.getConfirmedAt()).isEqualTo(dueAt);
        }

        @Test
        @DisplayName("기간 1초 뒤면 자동 확정한다")
        void afterDue() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt.plusSeconds(1)))
                .isEqualTo(TransitionResult.APPLIED);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.AUTO);
        }

        @Test
        @DisplayName("기간이 지났고 이미 확정이면 그대로 두고 ALREADY_DONE")
        void alreadyConfirmed() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);
            order.confirmItem(item.getId(), DELIVERED_AT.plusDays(1));

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt.plusDays(1)))
                .isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        }

        @Test
        @DisplayName("기간 전이라도 이미 직접 확정됐으면 확정 여부를 먼저 봐서 ALREADY_DONE")
        void confirmedBeforeDue() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_A_ID);
            order.confirmItem(item.getId(), DELIVERED_AT.plusDays(1));

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt.minusSeconds(1)))
                .isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        }

        @Test
        @DisplayName("배송 전이면 예외 없이 NOT_APPLICABLE")
        void notDelivered() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());
            OrderItem item = itemOf(order, SELLER_B_ID);

            assertThat(order.autoConfirmItem(item.getId(), POLICY, dueAt.plusDays(1)))
                .isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.ORDERED);
        }

        @Test
        @DisplayName("주문에 없는 주문상품이면 예외 없이 NOT_APPLICABLE")
        void missingItem() {
            Order order = OrderFixture.withItemIds(OrderFixture.deliveredToSellerA());

            assertThat(order.autoConfirmItem(999L, POLICY, dueAt.plusDays(1)))
                .isEqualTo(TransitionResult.NOT_APPLICABLE);
        }
    }

    @Nested
    @DisplayName("결제 완료 (payment.completed)")
    class MarkPaid {

        private static final Long ORDER_ID = 7L;

        @Test
        @DisplayName("회원 · 주문상품 구성 · 주문상품 금액 · 총액이 모두 같으면 결제완료가 되고 결제 ID와 시각을 남긴다")
        void markPaid() {
            Order order = OrderFixture.placedOrder();

            TransitionResult paid = OrderFixture.pay(order, PAYMENT_ID, PAID_AT);

            assertThat(paid).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("이미 취소된 주문은 결제완료가 되지 않는다 (먼저 바꾼 쪽이 이김)")
        void cancelledWins() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            TransitionResult paid = OrderFixture.pay(order, PAYMENT_ID, NOW.plusMinutes(12));

            assertThat(paid).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getPaymentId()).isNull();
            assertThat(order.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("같은 결제 완료가 두 번 와도 처음 값을 유지한다")
        void duplicateKeepsFirst() {
            Order order = OrderFixture.paidOrder();

            TransitionResult paid = OrderFixture.pay(order, 999L, PAID_AT.plusMinutes(1));

            assertThat(paid).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        }

        @Test
        @DisplayName("회원이 다르거나 비어 있으면 member-mismatch")
        void memberMismatch() {
            for (Long memberId : new Long[] {OTHER_MEMBER_ID, null}) {
                Order order = pendingWithIds();

                assertMismatch(order, () -> order.markPaid(PAYMENT_ID, memberId, order.getTotalAmount(),
                        OrderFixture.paidItemsOf(order), PAID_AT),
                    PaymentMismatchException.Reason.MEMBER_MISMATCH,
                    "expectedMemberId=" + BUYER_ID + " actualMemberId=" + memberId);
            }
        }

        @Test
        @DisplayName("주문상품이 하나 빠지면 item-missing(빠진 주문상품 ID)")
        void itemMissing() {
            Order order = pendingWithIds();
            List<Order.PaidItem> onlyFirst = OrderFixture.paidItemsOf(order).subList(0, 1);

            assertMismatch(order, payWith(order, onlyFirst),
                PaymentMismatchException.Reason.ITEM_MISSING, "orderItemId=2");
        }

        @Test
        @DisplayName("주문상품 목록이 비었거나 없으면(null) NPE 없이 item-missing")
        void emptyOrNullItemsAreMissing() {
            for (List<Order.PaidItem> items : java.util.Arrays.asList(List.<Order.PaidItem>of(), null)) {
                Order order = pendingWithIds();

                assertMismatch(order, payWith(order, items),
                    PaymentMismatchException.Reason.ITEM_MISSING, "orderItemId=1");
            }
        }

        @Test
        @DisplayName("주문에 없는 주문상품이 남으면 item-extra(남는 주문상품 ID)")
        void itemExtra() {
            Order order = pendingWithIds();
            List<Order.PaidItem> withExtra = new java.util.ArrayList<>(OrderFixture.paidItemsOf(order));
            withExtra.add(new Order.PaidItem(99L, 0L));

            assertMismatch(order, payWith(order, withExtra),
                PaymentMismatchException.Reason.ITEM_EXTRA, "orderItemId=99");
        }

        @Test
        @DisplayName("같은 주문상품이 두 번 오거나, 주문상품 ID가 비었거나, 빈 칸(null)이 있으면 item-extra")
        void duplicateOrBlankItemIsExtra() {
            List<List<Order.PaidItem>> cases = List.of(
                List.of(new Order.PaidItem(1L, 38_000L), new Order.PaidItem(2L, 15_000L), new Order.PaidItem(1L, 0L)),
                List.of(new Order.PaidItem(1L, 38_000L), new Order.PaidItem(2L, 15_000L), new Order.PaidItem(null, 0L)),
                java.util.Arrays.asList(new Order.PaidItem(1L, 38_000L), new Order.PaidItem(2L, 15_000L), null));
            for (List<Order.PaidItem> items : cases) {
                Order order = pendingWithIds();

                assertMismatch(order, payWith(order, items),
                    PaymentMismatchException.Reason.ITEM_EXTRA, "");
            }
        }

        @Test
        @DisplayName("한 주문상품 금액이 1원 적거나 많으면 item-amount-mismatch, 정확히 같으면 결제완료")
        void itemAmountBoundary() {
            for (long delta : new long[] {-1L, 1L}) {
                Order order = pendingWithIds();
                List<Order.PaidItem> items = List.of(
                    new Order.PaidItem(1L, 38_000L + delta), new Order.PaidItem(2L, 15_000L));

                assertMismatch(order, payWith(order, items),
                    PaymentMismatchException.Reason.ITEM_AMOUNT_MISMATCH,
                    "orderItemId=1 expected=38000 actual=" + (38_000L + delta));
            }

            Order exact = pendingWithIds();
            assertThat(exact.markPaid(PAYMENT_ID, BUYER_ID, 53_000L,
                List.of(new Order.PaidItem(1L, 38_000L), new Order.PaidItem(2L, 15_000L)), PAID_AT))
                .isEqualTo(TransitionResult.APPLIED);
        }

        @Test
        @DisplayName("총액은 같은데 주문상품끼리 금액이 바뀌면 item-amount-mismatch")
        void swappedItemAmounts() {
            Order order = pendingWithIds();
            List<Order.PaidItem> swapped = List.of(new Order.PaidItem(1L, 15_000L), new Order.PaidItem(2L, 38_000L));

            assertMismatch(order, payWith(order, swapped),
                PaymentMismatchException.Reason.ITEM_AMOUNT_MISMATCH, "orderItemId=1 expected=38000 actual=15000");
        }

        @Test
        @DisplayName("주문상품 금액은 모두 같아도 결제 금액이 총액보다 1원 많거나 적으면 total-amount-mismatch")
        void totalAmountMismatch() {
            for (long delta : new long[] {1L, -1L}) {
                Order order = pendingWithIds();
                long amount = order.getTotalAmount() + delta;

                assertMismatch(order, () -> order.markPaid(PAYMENT_ID, BUYER_ID, amount,
                        OrderFixture.paidItemsOf(order), PAID_AT),
                    PaymentMismatchException.Reason.TOTAL_AMOUNT_MISMATCH,
                    "expected=" + order.getTotalAmount() + " actual=" + amount);
            }
        }

        @Test
        @DisplayName("여러 개가 함께 틀리면 대조 순서(회원 → 구성 → 주문상품 금액 → 총액)에서 처음 걸린 사유 하나만 나온다")
        void firstReasonOnly() {
            Order memberFirst = pendingWithIds();
            assertMismatch(memberFirst, () -> memberFirst.markPaid(PAYMENT_ID, OTHER_MEMBER_ID, 1L, List.of(), PAID_AT),
                PaymentMismatchException.Reason.MEMBER_MISMATCH, "");

            Order compositionBeforeAmount = pendingWithIds();
            assertMismatch(compositionBeforeAmount, () -> compositionBeforeAmount.markPaid(PAYMENT_ID, BUYER_ID, 1L,
                    List.of(new Order.PaidItem(1L, 1L)), PAID_AT),
                PaymentMismatchException.Reason.ITEM_MISSING, "");

            Order itemAmountBeforeTotal = pendingWithIds();
            assertMismatch(itemAmountBeforeTotal, () -> itemAmountBeforeTotal.markPaid(PAYMENT_ID, BUYER_ID, 1L,
                    List.of(new Order.PaidItem(1L, 1L), new Order.PaidItem(2L, 15_000L)), PAID_AT),
                PaymentMismatchException.Reason.ITEM_AMOUNT_MISMATCH, "");
        }

        @Test
        @DisplayName("이미 결제완료면 회원 · 구성 · 금액이 달라도 예외 없이 ALREADY_DONE")
        void paidIgnoresMismatch() {
            Order order = OrderFixture.paidOrder();

            assertThat(order.markPaid(999L, OTHER_MEMBER_ID, order.getTotalAmount() + 1, List.of(),
                PAID_AT.plusMinutes(1)))
                .isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("취소된 주문은 회원 · 구성 · 금액이 달라도 예외 없이 NOT_APPLICABLE")
        void cancelledIgnoresMismatch() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(order.markPaid(PAYMENT_ID, null, order.getTotalAmount() - 1, null, NOW.plusMinutes(12)))
                .isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        }

        /**
         * 주문 ID 7 · 주문상품 ID 1(판매자 A, 38,000원) · 2(판매자 B, 15,000원) · 총액 53,000원인 결제대기 주문.
         */
        private Order pendingWithIds() {
            return OrderFixture.withId(OrderFixture.withItemIds(OrderFixture.placedOrder()), ORDER_ID);
        }

        /**
         * 회원 · 총액은 주문과 같고 주문상품별 금액만 주어진 대로인 결제 완료.
         */
        private org.assertj.core.api.ThrowableAssert.ThrowingCallable payWith(Order order, List<Order.PaidItem> items) {
            return () -> order.markPaid(PAYMENT_ID, BUYER_ID, order.getTotalAmount(), items, PAID_AT);
        }

        /**
         * 사유 하나 · 메시지(공백 없는 코드 형식) · 주문이 결제대기 그대로인지 확인한다.
         */
        private void assertMismatch(Order order, org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            PaymentMismatchException.Reason reason, String detail) {
            assertThatThrownBy(call)
                .isInstanceOfSatisfying(PaymentMismatchException.class,
                    e -> assertThat(e.getReason()).isEqualTo(reason))
                .hasMessageStartingWith("결제 불일치: orderId=" + ORDER_ID + " reason=" + reason.code())
                .hasMessageContaining(detail);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING_PAYMENT);
            assertThat(order.getPaymentId()).isNull();
            assertThat(order.getPaidAt()).isNull();
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("결제대기 주문은 사유와 함께 취소된다")
        void cancelPending() {
            Order order = OrderFixture.placedOrder();

            TransitionResult cancelled = order.cancel(Order.CancelReason.OUT_OF_STOCK);

            assertThat(cancelled).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("결제완료 주문은 취소되지 않는다 (결제 후 취소 없음)")
        void paidIsNotCancelled() {
            Order order = OrderFixture.paidOrder();

            TransitionResult cancelled = order.cancel(Order.CancelReason.PAYMENT_FAILED);

            assertThat(cancelled).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("이미 취소된 주문은 처음 사유를 유지한다")
        void keepsFirstReason() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.OUT_OF_STOCK);

            TransitionResult cancelled = order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(cancelled).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("구매자 취소는 결제대기 주문을 BUYER_CANCELLED로 취소한다 (#354)")
        void buyerCancelsPending() {
            Order order = OrderFixture.placedOrder();

            assertThat(order.cancel(Order.CancelReason.BUYER_CANCELLED)).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
        }

        @Test
        @DisplayName("구매자 취소만 결제완료 주문도 취소한다(배송 완료 · 구매확정된 주문상품이 없을 때) (#354)")
        void buyerCancelsPaid() {
            Order order = OrderFixture.paidOrder();

            assertThat(order.cancel(Order.CancelReason.BUYER_CANCELLED)).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Order.CancelReason.class, names = "BUYER_CANCELLED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("구매자 취소가 아닌 사유는 결제완료 주문을 취소하지 않는다 (#354 뒤에도 그대로)")
        void otherReasonsDoNotCancelPaid(Order.CancelReason reason) {
            Order order = OrderFixture.paidOrder();

            assertThat(order.cancel(reason)).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("배송 완료된 주문상품이 하나라도 있으면 구매자 취소도 결제완료 주문을 취소하지 않는다 (#354)")
        void buyerCannotCancelDelivered() {
            Order order = OrderFixture.deliveredToSellerA();

            assertThat(order.cancel(Order.CancelReason.BUYER_CANCELLED)).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("구매확정된 주문상품이 하나라도 있으면 구매자 취소도 결제완료 주문을 취소하지 않는다 (#354)")
        void buyerCannotCancelConfirmed() {
            Order order = OrderFixture.deliveredToSellerA();
            OrderItem sellerAItem = itemOf(order, SELLER_A_ID);
            order.confirmItem(sellerAItem.getId(), DELIVERED_AT.plusDays(1));

            assertThat(order.cancel(Order.CancelReason.BUYER_CANCELLED)).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
        }

        @Test
        @DisplayName("이미 취소된 주문은 구매자 취소여도 처음 사유를 유지한다 (#354)")
        void buyerCancelKeepsFirstReason() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(order.cancel(Order.CancelReason.BUYER_CANCELLED)).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
        }
    }

    @Nested
    @DisplayName("재고 차감 실패 취소 (stock.deduction-failed)")
    class CancelForOutOfStock {

        @Test
        @DisplayName("결제대기 주문은 OUT_OF_STOCK으로 취소되고, 사유가 온 주문상품에만 그 사유를 남긴다")
        void cancelsAndMarksOnlyFailedItems() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());

            TransitionResult result = order.cancelForOutOfStock(Map.of(1L, "OUT_OF_STOCK"));

            assertThat(result).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason)
                .containsExactly("OUT_OF_STOCK", null);
        }

        @Test
        @DisplayName("주문상품마다 다른 사유를 그대로 남긴다")
        void keepsReasonPerItem() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());

            order.cancelForOutOfStock(Map.of(1L, "PRODUCT_NOT_ON_SALE", 2L, "OPTION_NOT_ON_SALE"));

            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason)
                .containsExactly("PRODUCT_NOT_ON_SALE", "OPTION_NOT_ON_SALE");
        }

        @Test
        @DisplayName("사유가 하나도 없어도 주문은 OUT_OF_STOCK으로 취소되고 주문상품 사유는 모두 null")
        void cancelsWithoutItemReasons() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());

            TransitionResult result = order.cancelForOutOfStock(Map.of());

            assertThat(result).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.OUT_OF_STOCK);
            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason).containsExactly(null, null);
        }

        @Test
        @DisplayName("주문에 없는 주문상품 ID의 사유는 무시하고 주문은 취소한다")
        void ignoresUnknownOrderItemId() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());

            TransitionResult result = order.cancelForOutOfStock(Map.of(99L, "OUT_OF_STOCK", 2L, "OUT_OF_STOCK"));

            assertThat(result).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason)
                .containsExactly(null, "OUT_OF_STOCK");
        }

        @Test
        @DisplayName("현재 기준(결정 보류): 결제를 이미 요청한 결제대기 주문도 취소한다")
        void cancelsEvenAfterPaymentRequested() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());
            order.requestPayment(NOW);

            TransitionResult result = order.cancelForOutOfStock(Map.of(1L, "OUT_OF_STOCK"));

            assertThat(result).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        }

        @Test
        @DisplayName("결제완료 주문은 그대로 두고(NOT_APPLICABLE) 주문상품 사유도 남기지 않는다")
        void paidIsNotCancelled() {
            Order order = OrderFixture.paidOrder();

            TransitionResult result = order.cancelForOutOfStock(Map.of(1L, "OUT_OF_STOCK"));

            assertThat(result).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason).containsExactly(null, null);
        }

        @Test
        @DisplayName("이미 취소된 주문은 처음 사유를 유지하고(ALREADY_DONE) 주문상품 사유도 남기지 않는다")
        void alreadyCancelledKeepsFirstReason() {
            Order order = OrderFixture.withItemIds(OrderFixture.placedOrder());
            order.expirePayment(NOW.plus(PAYMENT_TIMEOUT));

            TransitionResult result = order.cancelForOutOfStock(Map.of(1L, "OUT_OF_STOCK"));

            assertThat(result).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
            assertThat(order.getItems()).extracting(OrderItem::getUnavailableReason).containsExactly(null, null);
        }
    }

    @Nested
    @DisplayName("요청 키 기록 (#351)")
    class RecordRequestKey {

        @Test
        @DisplayName("주문하기 직후 요청 키 · 비교값을 한 번 기록한다. 키 없이 만든 주문은 둘 다 null")
        void recordsOnce() {
            Order order = OrderFixture.placedOrder();
            assertThat(order.getIdempotencyKey()).isNull();
            assertThat(order.getRequestHash()).isNull();

            order.recordRequestKey("3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d", "a".repeat(64));

            assertThat(order.getIdempotencyKey()).isEqualTo("3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d");
            assertThat(order.getRequestHash()).isEqualTo("a".repeat(64));
        }

        @Test
        @DisplayName("이미 기록한 주문에 다시 기록하면 프로그래밍 오류로 IllegalStateException(값은 그대로)")
        void secondRecordIsRejected() {
            Order order = OrderFixture.placedOrder();
            order.recordRequestKey("3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d", "a".repeat(64));

            assertThatThrownBy(() -> order.recordRequestKey("00000000-0000-0000-0000-000000000000", "b".repeat(64)))
                .isInstanceOf(IllegalStateException.class);
            assertThat(order.getIdempotencyKey()).isEqualTo("3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d");
            assertThat(order.getRequestHash()).isEqualTo("a".repeat(64));
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

            order.recordDelivered(SELLER_A_ID, DELIVERED_AT.plusHours(1));

            assertThat(itemOf(order, SELLER_A_ID).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        }
    }

    @Nested
    @DisplayName("구매자 취소 · 취소 주문에 온 결제 완료 (#642, app에서 옮긴 규칙)")
    class BuyerCancelAndPaidAfterCancel {

        @Test
        @DisplayName("발송된 배송이 있으면 결제완료 주문을 구매자가 취소할 수 없다")
        void shippedIsNotCancelled() {
            Order order = OrderFixture.paidOrder();

            assertThat(order.cancelByBuyer(true)).isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
        }

        @Test
        @DisplayName("발송 전이면 BUYER_CANCELLED로 취소한다(결제대기 · 결제완료)")
        void notShippedIsCancelled() {
            Order paid = OrderFixture.paidOrder();
            Order pending = OrderFixture.placedOrder();

            assertThat(paid.cancelByBuyer(false)).isEqualTo(TransitionResult.APPLIED);
            assertThat(paid.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
            assertThat(pending.cancelByBuyer(false)).isEqualTo(TransitionResult.APPLIED);
        }

        @Test
        @DisplayName("이미 취소된 주문은 발송 여부와 상관없이 그대로(처음 사유 유지)")
        void alreadyCancelled() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(order.cancelByBuyer(true)).isEqualTo(TransitionResult.ALREADY_DONE);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.PAYMENT_EXPIRED);
        }

        @Test
        @DisplayName("결제 전에 취소된 주문에 온 결제 완료는 환불 확인 대상이다")
        void paidAfterCancel() {
            Order order = OrderFixture.placedOrder();
            order.cancel(Order.CancelReason.PAYMENT_EXPIRED);

            assertThat(order.isPaidAfterCancel(PAYMENT_ID)).isTrue();
            assertThat(order.isPaidAfterCancel(null)).isTrue();
        }

        @Test
        @DisplayName("결제완료 뒤 취소된 주문에 같은 결제 ID로 다시 온 결제 완료는 이미 반영한 중복이다")
        void samePaymentAfterPaidThenCancelled() {
            Order order = OrderFixture.paidOrder();
            order.cancel(Order.CancelReason.BUYER_CANCELLED);

            assertThat(order.isPaidAfterCancel(PAYMENT_ID)).isFalse();
            assertThat(order.isPaidAfterCancel(999L)).isTrue();
            assertThat(order.isPaidAfterCancel(null)).isTrue();
        }

        @Test
        @DisplayName("취소되지 않은 주문은 환불 확인 대상이 아니다")
        void notCancelled() {
            assertThat(OrderFixture.placedOrder().isPaidAfterCancel(PAYMENT_ID)).isFalse();
            assertThat(OrderFixture.paidOrder().isPaidAfterCancel(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("판매자 발송 전 몫 취소 (#655)")
    class CancelSellerItems {

        private static final Long CANCELLATION_ID = 90L;

        @Test
        @DisplayName("그 판매자 주문상품만 CANCELLED(사유 · 취소 ID · 시각), 다른 판매자 몫 · 주문 상태는 그대로")
        void onlySellersItems() {
            Order order = OrderFixture.paidOrder();

            List<OrderItem> cancelled = order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OUT_OF_STOCK,
                CANCELLATION_ID, NOW);

            OrderItem itemA = itemOf(order, SELLER_A_ID);
            assertThat(cancelled).containsExactly(itemA);
            assertThat(itemA.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
            assertThat(itemA.getCancelReason()).isEqualTo(OrderItem.CancelReason.OUT_OF_STOCK);
            assertThat(itemA.getCancellationId()).isEqualTo(CANCELLATION_ID);
            assertThat(itemA.getCancelledAt()).isEqualTo(NOW);
            assertThat(itemOf(order, SELLER_B_ID).getStatus()).isEqualTo(OrderItem.Status.ORDERED);
            assertThat(order.getStatus()).isEqualTo(Order.Status.PAID);
            assertThat(order.getCancelReason()).isNull();
        }

        @Test
        @DisplayName("마지막 판매자 몫까지 취소되면 주문도 CANCELLED(사유 SELLER_CANCELLED)")
        void lastShareCancelsOrder() {
            Order order = OrderFixture.paidOrder();
            order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OUT_OF_STOCK, CANCELLATION_ID, NOW);

            order.cancelSellerItems(SELLER_B_ID, OrderItem.CancelReason.OTHER, CANCELLATION_ID + 1, NOW);

            assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.SELLER_CANCELLED);
        }

        @Test
        @DisplayName("같은 판매자를 두 번 취소하면 두 번째는 빈 목록이고 처음 값(사유 · 취소 ID)을 유지한다")
        void twiceKeepsFirst() {
            Order order = OrderFixture.paidOrder();
            order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OUT_OF_STOCK, CANCELLATION_ID, NOW);

            assertThat(order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OTHER, 91L, NOW)).isEmpty();
            assertThat(itemOf(order, SELLER_A_ID).getCancellationId()).isEqualTo(CANCELLATION_ID);
            assertThat(itemOf(order, SELLER_A_ID).getCancelReason()).isEqualTo(OrderItem.CancelReason.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("결제완료가 아니면(결제대기 · 취소됨) 아무것도 바꾸지 않는다")
        void onlyPaid() {
            Order pending = OrderFixture.placedOrder();
            Order cancelled = OrderFixture.paidOrder();
            cancelled.cancelByBuyer(false);

            assertThat(pending.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OTHER, CANCELLATION_ID, NOW))
                .isEmpty();
            assertThat(cancelled.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OTHER, CANCELLATION_ID, NOW))
                .isEmpty();
            assertThat(itemOf(pending, SELLER_A_ID).getStatus()).isEqualTo(OrderItem.Status.ORDERED);
            assertThat(cancelled.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
        }

        @Test
        @DisplayName("배송 완료된 주문상품은 취소하지 않는다(그 판매자 몫이 이미 배송 완료)")
        void deliveredIsNotCancelled() {
            Order order = OrderFixture.deliveredToSellerA();

            assertThat(order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OTHER, CANCELLATION_ID, NOW))
                .isEmpty();
            assertThat(itemOf(order, SELLER_A_ID).getStatus()).isEqualTo(OrderItem.Status.ORDERED);
        }

        @Test
        @DisplayName("취소된 주문상품은 배송 완료 기록 · 직접 구매확정(409 ORDER_ITEM_CANCELLED) · 자동 구매확정 대상이 아니다")
        void cancelledItemIsExcludedFromFollowUps() {
            Order order = OrderFixture.paidOrder();
            order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OUT_OF_STOCK, CANCELLATION_ID, NOW);
            OrderItem itemA = itemOf(order, SELLER_A_ID);

            order.recordDelivered(SELLER_A_ID, DELIVERED_AT);

            assertThat(itemA.getDeliveredAt()).isNull();
            assertThatThrownBy(() -> order.confirmItem(itemA.getId(), DELIVERED_AT))
                .isInstanceOf(OrderRuleViolationException.class)
                .extracting("rule").isEqualTo(Rule.ORDER_ITEM_CANCELLED);
            assertThat(order.autoConfirmItem(itemA.getId(), POLICY, DELIVERED_AT.plus(AUTO_CONFIRM_AFTER).plusDays(1)))
                .isEqualTo(TransitionResult.NOT_APPLICABLE);
            assertThat(itemA.getStatus()).isEqualTo(OrderItem.Status.CANCELLED);
        }

        @Test
        @DisplayName("일부 몫이 취소된 결제완료 주문도 구매자가 발송 전이면 전체 취소할 수 있다(사유 BUYER_CANCELLED)")
        void buyerCanCancelAfterPartial() {
            Order order = OrderFixture.paidOrder();
            order.cancelSellerItems(SELLER_A_ID, OrderItem.CancelReason.OUT_OF_STOCK, CANCELLATION_ID, NOW);

            assertThat(order.cancelByBuyer(false)).isEqualTo(TransitionResult.APPLIED);
            assertThat(order.getCancelReason()).isEqualTo(Order.CancelReason.BUYER_CANCELLED);
        }
    }
}
