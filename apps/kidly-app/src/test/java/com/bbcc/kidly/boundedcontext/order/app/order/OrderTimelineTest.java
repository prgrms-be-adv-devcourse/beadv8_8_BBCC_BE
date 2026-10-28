package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline.DeliveryDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline.ItemDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline.Step;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline.StepName;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderTimeline.StepStatus;
import com.bbcc.kidly.boundedcontext.order.domain.delivery.Delivery;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 타임라인 조립 규칙. 실제 Order · Delivery로 만들고 시각은 실행 시각 기준 상대값으로 준다.
 * 주문상품 2개: 41번(판매자 5, 아동 티셔츠) · 42번(판매자 8, 아동 바지). 배송 건은 31번(판매자 5) · 32번(판매자 8).
 */
class OrderTimelineTest {

    private static final Order.Policy POLICY = new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15));
    private static final Long ORDER_ID = 12L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_A = 5L;
    private static final Long SELLER_B = 8L;
    private static final Map<Long, String> SELLER_NAMES = Map.of(SELLER_A, "키들리상점", SELLER_B, "꼬마옷장");
    private static final LocalDateTime ORDERED_AT = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusDays(20);

    @Nested
    @DisplayName("결제대기")
    class PendingPayment {

        @Test
        @DisplayName("결제 요청 전이면 CREATED만 DONE, 결제 요청 · 결제 · 배송 단계는 WAITING, 배송 details는 [], 확정 details는 주문상품 WAITING")
        void beforePaymentRequest() {
            Order order = placed();

            OrderTimeline timeline = OrderTimeline.from(order, List.of(), SELLER_NAMES);

            assertThat(timeline.orderId()).isEqualTo(ORDER_ID);
            assertThat(timeline.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(timeline.cancelReason()).isNull();
            assertThat(timeline.paymentDueAt()).isEqualTo(order.getPaymentDueAt()).isNotNull();
            assertThat(timeline.currentStep()).isEqualTo(StepName.CREATED);
            assertThat(timeline.steps()).extracting(Step::step).containsExactly(StepName.CREATED,
                StepName.PAYMENT_REQUESTED, StepName.PAID, StepName.SHIPPED, StepName.DELIVERED, StepName.CONFIRMED);
            assertStep(timeline, StepName.CREATED, StepStatus.DONE, ORDERED_AT);
            assertStep(timeline, StepName.PAYMENT_REQUESTED, StepStatus.WAITING, null);
            assertStep(timeline, StepName.PAID, StepStatus.WAITING, null);
            assertStep(timeline, StepName.SHIPPED, StepStatus.WAITING, null);
            assertThat(step(timeline, StepName.SHIPPED).deliveries()).isEmpty();
            assertThat(step(timeline, StepName.DELIVERED).deliveries()).isEmpty();
            assertStep(timeline, StepName.CONFIRMED, StepStatus.WAITING, null);
            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, "아동 티셔츠", StepStatus.WAITING, null, null),
                new ItemDetail(42L, "아동 바지", StepStatus.WAITING, null, null));
            assertThat(timeline.steps()).extracting(Step::approximate).containsOnly(false);
            assertNoDetails(step(timeline, StepName.CREATED));
        }

        @Test
        @DisplayName("결제를 요청했으면 PAYMENT_REQUESTED가 DONE(요청 시각)이고 현재 단계가 된다")
        void afterPaymentRequest() {
            Order order = placed();
            order.requestPayment(ORDERED_AT.plusSeconds(1));

            OrderTimeline timeline = OrderTimeline.from(order, List.of(), SELLER_NAMES);

            assertStep(timeline, StepName.PAYMENT_REQUESTED, StepStatus.DONE, ORDERED_AT.plusSeconds(1));
            assertStep(timeline, StepName.PAID, StepStatus.WAITING, null);
            assertThat(timeline.currentStep()).isEqualTo(StepName.PAYMENT_REQUESTED);
            assertThat(timeline.paymentDueAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("결제완료 · 배송 · 구매확정")
    class Paid {

        @Test
        @DisplayName("결제완료 직후면 PAID가 DONE, 배송 details는 판매자별 WAITING, 결제 마감은 null")
        void justPaid() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertThat(timeline.status()).isEqualTo("PAID");
            assertThat(timeline.paymentDueAt()).isNull();
            assertThat(timeline.currentStep()).isEqualTo(StepName.PAID);
            assertStep(timeline, StepName.PAID, StepStatus.DONE, ORDERED_AT.plusSeconds(2));
            assertStep(timeline, StepName.SHIPPED, StepStatus.WAITING, null);
            assertThat(step(timeline, StepName.SHIPPED).deliveries()).containsExactly(
                new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.WAITING, null),
                new DeliveryDetail(32L, SELLER_B, "꼬마옷장", StepStatus.WAITING, null));
            assertStep(timeline, StepName.DELIVERED, StepStatus.WAITING, null);
            assertStep(timeline, StepName.CONFIRMED, StepStatus.WAITING, null);
        }

        @Test
        @DisplayName("판매자 둘 중 하나만 발송했으면 SHIPPED는 PARTIAL(at null)이고 현재 단계, 발송한 건만 DONE(발송 시각)")
        void partiallyShipped() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            deliveries.get(0).ship(Delivery.Carrier.CJ, "1234", ORDERED_AT.plusHours(2));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.PARTIAL, null);
            assertThat(step(timeline, StepName.SHIPPED).deliveries()).containsExactly(
                new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.DONE, ORDERED_AT.plusHours(2)),
                new DeliveryDetail(32L, SELLER_B, "꼬마옷장", StepStatus.WAITING, null));
            assertStep(timeline, StepName.DELIVERED, StepStatus.WAITING, null);
            assertThat(timeline.currentStep()).isEqualTo(StepName.SHIPPED);
        }

        @Test
        @DisplayName("모두 배송 완료면 SHIPPED · DELIVERED가 DONE이고 at은 가장 늦은 건의 시각")
        void allDelivered() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            shipAndDeliver(order, deliveries.get(0), ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));
            shipAndDeliver(order, deliveries.get(1), ORDERED_AT.plusHours(2), ORDERED_AT.plusDays(2));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.DONE, ORDERED_AT.plusHours(3));
            assertStep(timeline, StepName.DELIVERED, StepStatus.DONE, ORDERED_AT.plusDays(2));
            assertThat(step(timeline, StepName.DELIVERED).deliveries()).containsExactly(
                new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.DONE, ORDERED_AT.plusDays(1)),
                new DeliveryDetail(32L, SELLER_B, "꼬마옷장", StepStatus.DONE, ORDERED_AT.plusDays(2)));
            assertStep(timeline, StepName.CONFIRMED, StepStatus.WAITING, null);
            assertThat(timeline.currentStep()).isEqualTo(StepName.DELIVERED);
        }

        @Test
        @DisplayName("주문상품 하나만 직접 확정했으면 CONFIRMED는 PARTIAL, 확정한 건은 DONE · DIRECT")
        void partiallyConfirmed() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            shipAndDeliver(order, deliveries.get(0), ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));
            shipAndDeliver(order, deliveries.get(1), ORDERED_AT.plusHours(2), ORDERED_AT.plusDays(2));
            order.confirmItem(41L, ORDERED_AT.plusDays(3));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertStep(timeline, StepName.CONFIRMED, StepStatus.PARTIAL, null);
            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, "아동 티셔츠", StepStatus.DONE, ORDERED_AT.plusDays(3), "DIRECT"),
                new ItemDetail(42L, "아동 바지", StepStatus.WAITING, null, null));
            assertThat(timeline.currentStep()).isEqualTo(StepName.CONFIRMED);
        }

        @Test
        @DisplayName("직접 · 자동으로 모두 확정했으면 CONFIRMED는 DONE(가장 늦은 확정 시각), confirmType은 건마다 DIRECT · AUTO")
        void allConfirmed() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            shipAndDeliver(order, deliveries.get(0), ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));
            shipAndDeliver(order, deliveries.get(1), ORDERED_AT.plusHours(2), ORDERED_AT.plusDays(2));
            order.confirmItem(41L, ORDERED_AT.plusDays(3));
            order.autoConfirmItem(42L, POLICY, ORDERED_AT.plusDays(17));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertStep(timeline, StepName.CONFIRMED, StepStatus.DONE, ORDERED_AT.plusDays(17));
            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, "아동 티셔츠", StepStatus.DONE, ORDERED_AT.plusDays(3), "DIRECT"),
                new ItemDetail(42L, "아동 바지", StepStatus.DONE, ORDERED_AT.plusDays(17), "AUTO"));
            assertThat(timeline.currentStep()).isEqualTo(StepName.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("결제 전 취소")
    class CancelledBeforePayment {

        @Test
        @DisplayName("결제 요청 전 재고 부족 취소: 결제 요청 NOT_APPLICABLE → CANCELLED(updatedAt, approximate) → 나머지 NOT_APPLICABLE")
        void outOfStockBeforeRequest() {
            Order order = placed();
            order.cancelForOutOfStock(Map.of(41L, "OUT_OF_STOCK"));
            setTime(order, "updatedAt", ORDERED_AT.plusSeconds(1));

            OrderTimeline timeline = OrderTimeline.from(order, List.of(), SELLER_NAMES);

            assertThat(timeline.status()).isEqualTo("CANCELLED");
            assertThat(timeline.cancelReason()).isEqualTo("OUT_OF_STOCK");
            assertThat(timeline.paymentDueAt()).isNull();
            assertThat(timeline.currentStep()).isEqualTo(StepName.CANCELLED);
            assertThat(timeline.steps()).extracting(Step::step).containsExactly(StepName.CREATED,
                StepName.PAYMENT_REQUESTED, StepName.CANCELLED, StepName.SHIPPED, StepName.DELIVERED,
                StepName.CONFIRMED);
            assertStep(timeline, StepName.PAYMENT_REQUESTED, StepStatus.NOT_APPLICABLE, null);
            assertStep(timeline, StepName.CANCELLED, StepStatus.DONE, ORDERED_AT.plusSeconds(1));
            assertThat(step(timeline, StepName.CANCELLED).approximate()).isTrue();
            for (StepName rest : List.of(StepName.SHIPPED, StepName.DELIVERED, StepName.CONFIRMED)) {
                assertStep(timeline, rest, StepStatus.NOT_APPLICABLE, null);
                assertNoDetails(step(timeline, rest));
            }
            assertThat(timeline.steps()).filteredOn(s -> s.step() != StepName.CANCELLED)
                .extracting(Step::approximate).containsOnly(false);
        }

        @Test
        @DisplayName("결제 요청 뒤 결제 실패 · 결제 마감 만료 취소: PAYMENT_REQUESTED는 DONE, PAID 없이 CANCELLED가 현재 단계")
        void cancelledAfterRequest() {
            Order failed = placed();
            failed.requestPayment(ORDERED_AT.plusSeconds(1));
            failed.cancel(Order.CancelReason.PAYMENT_FAILED);
            setTime(failed, "updatedAt", ORDERED_AT.plusSeconds(5));
            Order expired = placed();
            expired.requestPayment(ORDERED_AT.plusSeconds(1));
            expired.expirePayment(ORDERED_AT.plusMinutes(11));
            setTime(expired, "updatedAt", ORDERED_AT.plusMinutes(11));

            OrderTimeline failedTimeline = OrderTimeline.from(failed, List.of(), SELLER_NAMES);
            OrderTimeline expiredTimeline = OrderTimeline.from(expired, List.of(), SELLER_NAMES);

            assertThat(failedTimeline.cancelReason()).isEqualTo("PAYMENT_FAILED");
            assertThat(expiredTimeline.cancelReason()).isEqualTo("PAYMENT_EXPIRED");
            for (OrderTimeline timeline : List.of(failedTimeline, expiredTimeline)) {
                assertThat(timeline.steps()).extracting(Step::step).containsExactly(StepName.CREATED,
                    StepName.PAYMENT_REQUESTED, StepName.CANCELLED, StepName.SHIPPED, StepName.DELIVERED,
                    StepName.CONFIRMED);
                assertStep(timeline, StepName.PAYMENT_REQUESTED, StepStatus.DONE, ORDERED_AT.plusSeconds(1));
                assertThat(timeline.currentStep()).isEqualTo(StepName.CANCELLED);
            }
            assertStep(failedTimeline, StepName.CANCELLED, StepStatus.DONE, ORDERED_AT.plusSeconds(5));
            assertStep(expiredTimeline, StepName.CANCELLED, StepStatus.DONE, ORDERED_AT.plusMinutes(11));
        }
    }

    @Nested
    @DisplayName("결제 뒤 구매자 취소 (#354)")
    class CancelledAfterPayment {

        @Test
        @DisplayName("PAID → CANCELLED(approximate) 순서, 배송 · 확정 단계와 그 details(배송 건 · 주문상품)는 NOT_APPLICABLE")
        void buyerCancelAfterPayment() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            order.cancel(Order.CancelReason.BUYER_CANCELLED);
            deliveries.forEach(Delivery::cancel);
            setTime(order, "updatedAt", ORDERED_AT.plusMinutes(30));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertThat(timeline.status()).isEqualTo("CANCELLED");
            assertThat(timeline.cancelReason()).isEqualTo("BUYER_CANCELLED");
            assertThat(timeline.paymentDueAt()).isNull();
            assertThat(timeline.currentStep()).isEqualTo(StepName.CANCELLED);
            assertThat(timeline.steps()).extracting(Step::step).containsExactly(StepName.CREATED,
                StepName.PAYMENT_REQUESTED, StepName.PAID, StepName.CANCELLED, StepName.SHIPPED, StepName.DELIVERED,
                StepName.CONFIRMED);
            assertStep(timeline, StepName.PAID, StepStatus.DONE, ORDERED_AT.plusSeconds(2));
            assertStep(timeline, StepName.CANCELLED, StepStatus.DONE, ORDERED_AT.plusMinutes(30));
            assertThat(step(timeline, StepName.CANCELLED).approximate()).isTrue();
            for (StepName name : List.of(StepName.SHIPPED, StepName.DELIVERED)) {
                assertStep(timeline, name, StepStatus.NOT_APPLICABLE, null);
                assertThat(step(timeline, name).deliveries()).as(name.name()).containsExactly(
                    new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.NOT_APPLICABLE, null),
                    new DeliveryDetail(32L, SELLER_B, "꼬마옷장", StepStatus.NOT_APPLICABLE, null));
            }
            assertStep(timeline, StepName.CONFIRMED, StepStatus.NOT_APPLICABLE, null);
            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, "아동 티셔츠", StepStatus.NOT_APPLICABLE, null, null),
                new ItemDetail(42L, "아동 바지", StepStatus.NOT_APPLICABLE, null, null));
        }

        @Test
        @DisplayName("결제 뒤 취소인데 배송 건이 없으면(배송 생성 유실) 배송 단계 details는 [], 확정은 주문상품별 NOT_APPLICABLE")
        void buyerCancelAfterPaymentWithoutDeliveries() {
            Order order = paid();
            order.cancel(Order.CancelReason.BUYER_CANCELLED);

            OrderTimeline timeline = OrderTimeline.from(order, List.of(), SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.NOT_APPLICABLE, null);
            assertNoDetails(step(timeline, StepName.SHIPPED));
            assertNoDetails(step(timeline, StepName.DELIVERED));
            assertThat(step(timeline, StepName.CONFIRMED).items()).hasSize(2)
                .extracting(ItemDetail::status).containsOnly(StepStatus.NOT_APPLICABLE);
        }
    }

    @Nested
    @DisplayName("빠진 데이터 방어")
    class MissingData {

        @Test
        @DisplayName("결제완료인데 배송 건이 하나도 없으면 SHIPPED · DELIVERED는 WAITING · [], 현재 단계는 PAID")
        void paidWithoutDeliveries() {
            OrderTimeline timeline = OrderTimeline.from(paid(), List.of(), SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.WAITING, null);
            assertThat(step(timeline, StepName.SHIPPED).deliveries()).isEmpty();
            assertStep(timeline, StepName.DELIVERED, StepStatus.WAITING, null);
            assertThat(step(timeline, StepName.DELIVERED).deliveries()).isEmpty();
            assertThat(timeline.currentStep()).isEqualTo(StepName.PAID);
        }

        @Test
        @DisplayName("판매자 둘 중 한 판매자의 배송 건만 있고 그 건이 배송 완료여도 DONE이 아니라 PARTIAL, 없는 건은 만들지 않는다")
        void missingSellerDeliveryIsNotDone() {
            Order order = paid();
            Delivery onlyA = deliveries().get(0);
            shipAndDeliver(order, onlyA, ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));

            OrderTimeline timeline = OrderTimeline.from(order, List.of(onlyA), SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.PARTIAL, null);
            assertStep(timeline, StepName.DELIVERED, StepStatus.PARTIAL, null);
            assertThat(step(timeline, StepName.DELIVERED).deliveries()).containsExactly(
                new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.DONE, ORDERED_AT.plusDays(1)));
        }

        @Test
        @DisplayName("가장 늦은 시각이 두 건 같아도(동률) 단계 at은 그 시각")
        void latestAtTie() {
            Order order = paid();
            List<Delivery> deliveries = deliveries();
            shipAndDeliver(order, deliveries.get(0), ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));
            shipAndDeliver(order, deliveries.get(1), ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));

            OrderTimeline timeline = OrderTimeline.from(order, deliveries, SELLER_NAMES);

            assertStep(timeline, StepName.SHIPPED, StepStatus.DONE, ORDERED_AT.plusHours(3));
            assertStep(timeline, StepName.DELIVERED, StepStatus.DONE, ORDERED_AT.plusDays(1));
        }

        @Test
        @DisplayName("주문 당시 상품명이 없는 옛 주문상품은 productName null")
        void missingProductName() {
            Order order = Order.place(BUYER_ID, List.of(new Order.RequestedItem(new OrderItem.ProductSnapshot(
                    1L, 1L, SELLER_A, null, null, null, 25_000L, 1L), true, null, 1, 25_000L)),
                new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY, ORDERED_AT);
            setField(OrderItem.class, order.getItems().get(0), "id", 41L);

            OrderTimeline timeline = OrderTimeline.from(order, List.of(), SELLER_NAMES);

            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, null, StepStatus.WAITING, null, null));
        }

        @Test
        @DisplayName("상품 스냅샷이 아예 없는 옛 주문상품이면 productName null, 판매자 수에서 빠지고 예외 없음")
        void missingProductSnapshot() {
            Order order = paid();
            Delivery onlyA = deliveries().get(0);
            shipAndDeliver(order, onlyA, ORDERED_AT.plusHours(3), ORDERED_AT.plusDays(1));
            setField(OrderItem.class, order.getItems().get(1), "product", null);

            OrderTimeline timeline = OrderTimeline.from(order, List.of(onlyA), SELLER_NAMES);

            assertThat(step(timeline, StepName.CONFIRMED).items()).containsExactly(
                new ItemDetail(41L, "아동 티셔츠", StepStatus.WAITING, null, null),
                new ItemDetail(42L, null, StepStatus.WAITING, null, null));
            assertStep(timeline, StepName.DELIVERED, StepStatus.DONE, ORDERED_AT.plusDays(1));
        }

        @Test
        @DisplayName("판매자 복사본이 없으면 sellerName은 null")
        void missingSellerName() {
            OrderTimeline timeline = OrderTimeline.from(paid(), deliveries(), Map.of(SELLER_A, "키들리상점"));

            assertThat(step(timeline, StepName.SHIPPED).deliveries()).containsExactly(
                new DeliveryDetail(31L, SELLER_A, "키들리상점", StepStatus.WAITING, null),
                new DeliveryDetail(32L, SELLER_B, null, StepStatus.WAITING, null));
        }
    }

    private static void assertStep(OrderTimeline timeline, StepName name, StepStatus status, LocalDateTime at) {
        Step step = step(timeline, name);
        assertThat(step.status()).as(name + " status").isEqualTo(status);
        assertThat(step.at()).as(name + " at").isEqualTo(at);
    }

    private static void assertNoDetails(Step step) {
        assertThat(step.deliveries()).as(step.step() + " deliveries").isEmpty();
        assertThat(step.items()).as(step.step() + " items").isEmpty();
    }

    private static Step step(OrderTimeline timeline, StepName name) {
        return timeline.steps().stream().filter(step -> step.step() == name).findFirst().orElseThrow();
    }

    private static Order placed() {
        Order order = Order.place(BUYER_ID, List.of(
                new Order.RequestedItem(new OrderItem.ProductSnapshot(
                    1L, 1L, SELLER_A, "아동 티셔츠", null, "110", 25_000L, 1L), true, null, 1, 25_000L),
                new Order.RequestedItem(new OrderItem.ProductSnapshot(
                    2L, 2L, SELLER_B, "아동 바지", null, "120", 30_000L, 2L), true, null, 1, 30_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"), POLICY, ORDERED_AT);
        setField(Order.class, order, "id", ORDER_ID);
        setField(OrderItem.class, order.getItems().get(0), "id", 41L);
        setField(OrderItem.class, order.getItems().get(1), "id", 42L);
        setTime(order, "createdAt", ORDERED_AT);
        setTime(order, "updatedAt", ORDERED_AT);
        return order;
    }

    private static Order paid() {
        Order order = placed();
        order.requestPayment(ORDERED_AT.plusSeconds(1));
        order.markPaid(500L, BUYER_ID, 55_000L, List.of(
            new Order.PaidItem(41L, 25_000L), new Order.PaidItem(42L, 30_000L)), ORDERED_AT.plusSeconds(2));
        return order;
    }

    private static List<Delivery> deliveries() {
        Delivery first = Delivery.create(ORDER_ID, SELLER_A);
        Delivery second = Delivery.create(ORDER_ID, SELLER_B);
        setField(Delivery.class, first, "id", 31L);
        setField(Delivery.class, second, "id", 32L);
        return List.of(first, second);
    }

    private static void shipAndDeliver(Order order, Delivery delivery, LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {
        delivery.ship(Delivery.Carrier.CJ, "1234", shippedAt);
        delivery.complete(deliveredAt);
        order.recordDelivered(delivery.getSellerId(), deliveredAt);
    }

    private static void setTime(Order order, String name, LocalDateTime value) {
        setField(BaseTimeEntity.class, order, name, value);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
