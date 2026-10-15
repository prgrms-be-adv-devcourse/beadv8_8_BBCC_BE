package com.bbcc.kidly.boundedcontext.order.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
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
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_SHIPPED);
        assertThat(delivery.getStatus()).isEqualTo(Delivery.Status.READY);
    }

    @Test
    @DisplayName("이미 발송한 배송은 다시 발송할 수 없다 (409 ALREADY_SHIPPED)")
    void cannotShipTwice() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.HANJIN, "999", SHIPPED_AT.plusHours(1)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.ALREADY_SHIPPED);
        assertThat(delivery.getCarrier()).isEqualTo(Delivery.Carrier.CJ);
    }

    @Test
    @DisplayName("배송 완료된 배송도 다시 발송할 수 없다 (409 ALREADY_SHIPPED, 배송 완료 그대로)")
    void cannotShipAfterDelivered() {
        Delivery delivery = Delivery.create(ORDER_ID, SELLER_A_ID);
        delivery.ship(Delivery.Carrier.CJ, "1234567890", SHIPPED_AT);
        delivery.complete(COMPLETED_AT);

        assertThatThrownBy(() -> delivery.ship(Delivery.Carrier.HANJIN, "999", COMPLETED_AT.plusHours(1)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.ALREADY_SHIPPED);
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
}
