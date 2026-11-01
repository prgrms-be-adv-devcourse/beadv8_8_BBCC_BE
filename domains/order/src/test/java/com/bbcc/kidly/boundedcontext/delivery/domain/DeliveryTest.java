package com.bbcc.kidly.boundedcontext.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.delivery.domain.DeliveryRuleViolationException.Rule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 판매자별 배송 규칙(주문 정리 5.4). 판매자 본인 확인은 app이 한다.
 */
class DeliveryTest {

    private static final Long SELLER_A_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 12, 0);
    private static final Long ORDER_ID = 300L;
    private static final LocalDateTime SHIPPED_AT = NOW.plusDays(1);
    private static final LocalDateTime COMPLETED_AT = NOW.plusDays(2);

    @Test
    @DisplayName("결제완료 후 판매자마다 배송 준비 상태로 만들어진다")
    void create() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);

        assertThat(delivery.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(delivery.getSellerId()).isEqualTo(SELLER_A_ID);
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.READY);
        assertThat(delivery.getCarrier()).isNull();
        assertThat(delivery.getTrackingNumber()).isNull();
    }

    @Test
    @DisplayName("송장을 등록하면 배송 중이 된다")
    void ship() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);

        delivery.ship(Delivery.Carrier.DUMMY, "1234567890", SHIPPED_AT);

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(delivery.getCarrier()).isEqualTo(Delivery.Carrier.DUMMY);
        assertThat(delivery.getTrackingNumber()).isEqualTo("1234567890");
        assertThat(delivery.getShippedAt()).isEqualTo(SHIPPED_AT);
    }

    @Test
    @DisplayName("배송 중이면 배송 완료할 수 있다")
    void complete() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);

        delivery.complete(COMPLETED_AT);

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    @DisplayName("송장 없이 배송 완료할 수 없다 (409 NOT_SHIPPED)")
    void cannotCompleteBeforeShip() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);

        assertThatThrownBy(() -> delivery.complete(COMPLETED_AT))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.NOT_SHIPPED);
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.READY);
    }

    @Test
    @DisplayName("이미 발송한 배송은 다시 발송할 수 없다 (409 ALREADY_SHIPPED)")
    void cannotShipTwice() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.HANJIN, "999", SHIPPED_AT.plusHours(1)))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_SHIPPED);
        assertThat(delivery.getCarrier()).isEqualTo(Delivery.Carrier.CJ);
    }

    @Test
    @DisplayName("배송 완료된 배송도 다시 발송할 수 없다 (409 ALREADY_SHIPPED, 배송 완료 그대로)")
    void cannotShipAfterDelivered() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        delivery.complete(COMPLETED_AT);

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.HANJIN, "999", COMPLETED_AT.plusHours(1)))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_SHIPPED);
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("이미 배송 완료면 예외 없이 처음 완료 시각을 유지한다 (배송 완료를 두 번 눌러도 200)")
    void completeTwiceKeepsFirst() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        delivery.complete(COMPLETED_AT);

        delivery.complete(COMPLETED_AT.plusHours(1));

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    @DisplayName("발송 전이면 구매자 취소로 CANCELLED가 되고, 다시 취소해도 그대로다 (#354)")
    void cancelReady() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);

        delivery.cancel();
        delivery.cancel();

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.CANCELLED);
        assertThat(delivery.getCarrier()).isNull();
        assertThat(delivery.getShippedAt()).isNull();
    }

    @Test
    @DisplayName("발송 · 배송 완료한 배송은 취소할 수 없다(서비스가 먼저 거르므로 여기 오면 불변식 위반) (#354)")
    void cannotCancelShipped() {
        Delivery shipping = Delivery.create(ORDER_ID, SELLER_A_ID);
        shipping.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        Delivery delivered = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivered.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        delivered.complete(COMPLETED_AT);

        assertThatThrownBy(shipping::cancel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(delivered::cancel).isInstanceOf(IllegalStateException.class);
        assertThat(shipping.getStatus()).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(delivered.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("구매자가 취소한 배송은 발송 · 배송 완료할 수 없다 (409 DELIVERY_CANCELLED, 취소 그대로) (#354)")
    void cancelledCannotShipOrComplete() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.cancel();

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.DELIVERY_CANCELLED);
        assertThatThrownBy(() -> delivery.complete(COMPLETED_AT))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.DELIVERY_CANCELLED);
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.CANCELLED);
        assertThat(delivery.getCarrier()).isNull();
    }

    @Test
    @DisplayName("판매자 발송 전 취소: READY면 CANCELLED (#655)")
    void cancelBySellerReady() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);

        delivery.cancelBySeller();

        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.CANCELLED);
    }

    @Test
    @DisplayName("판매자 발송 전 취소: 이미 취소면(두 번째 요청 · 구매자 취소) 409 ALREADY_CANCELLED, 발송 · 배송 완료면 409 ALREADY_SHIPPED (#655)")
    void cancelBySellerOnlyReady() {
        Delivery cancelled = Delivery.create(ORDER_ID, SELLER_A_ID);
        cancelled.cancelBySeller();
        Delivery buyerCancelled = Delivery.create(ORDER_ID, SELLER_A_ID);
        buyerCancelled.cancel();
        Delivery shipping = Delivery.create(ORDER_ID, SELLER_A_ID);
        shipping.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        Delivery delivered = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivered.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        delivered.complete(COMPLETED_AT);

        assertThatThrownBy(cancelled::cancelBySeller).isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_CANCELLED);
        assertThatThrownBy(buyerCancelled::cancelBySeller).isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_CANCELLED);
        assertThatThrownBy(shipping::cancelBySeller).isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_SHIPPED);
        assertThatThrownBy(delivered::cancelBySeller).isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.ALREADY_SHIPPED);
        assertThat(shipping.getStatus()).isEqualTo(Delivery.Status.SHIPPING);
        assertThat(delivered.getStatus()).isEqualTo(Delivery.Status.DELIVERED);
    }

    @Test
    @DisplayName("판매자가 취소한 배송은 발송 · 배송 완료할 수 없다 (409 DELIVERY_CANCELLED) (#655)")
    void sellerCancelledCannotShip() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.cancelBySeller();

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT))
            .isInstanceOf(DeliveryRuleViolationException.class)
            .extracting("rule").isEqualTo(Rule.DELIVERY_CANCELLED);
        assertThat(delivery.completeByTracking(COMPLETED_AT)).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.CANCELLED);
    }
}
