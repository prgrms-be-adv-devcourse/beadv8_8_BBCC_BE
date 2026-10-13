package com.bbcc.kidly.boundedcontext.order.domain.order;

import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.DELIVERED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_A_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_B_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.itemOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구매확정 규칙(주문 정리 5.1). 직접 · 자동 모두 같은 confirm 경로를 쓴다.
 */
class OrderItemTest {

    private static final LocalDateTime CONFIRMED_AT = DELIVERED_AT.plusDays(1);

    @Test
    @DisplayName("배송 완료된 주문상품은 직접 구매확정하면 확정 시각 · 방식 · 핏 피드백을 남긴다")
    void confirmDirect() {
        OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_A_ID);

        item.confirm(OrderItem.ConfirmType.DIRECT, OrderItem.FitFeedback.FIT, CONFIRMED_AT);

        assertThat(item.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
        assertThat(item.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        assertThat(item.getFitFeedback()).isEqualTo(OrderItem.FitFeedback.FIT);
    }

    @Test
    @DisplayName("핏 피드백 없이도 구매확정할 수 있다")
    void fitFeedbackIsOptional() {
        OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_A_ID);

        item.confirm(OrderItem.ConfirmType.AUTO, null, CONFIRMED_AT);

        assertThat(item.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
        assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.AUTO);
        assertThat(item.getFitFeedback()).isNull();
    }

    @Test
    @DisplayName("배송 완료 전에는 구매확정할 수 없다 (409)")
    void requiresDelivered() {
        OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_B_ID);

        assertThatThrownBy(() -> item.confirm(OrderItem.ConfirmType.DIRECT, null, CONFIRMED_AT))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.NOT_DELIVERED);
        assertThat(item.getStatus()).isEqualTo(OrderItem.Status.ORDERED);
    }

    @Test
    @DisplayName("이미 확정된 주문상품은 다시 확정해도 처음 값을 유지한다 (직접 · 자동이 겹쳐도 한 번)")
    void confirmOnlyOnce() {
        OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_A_ID);
        item.confirm(OrderItem.ConfirmType.DIRECT, OrderItem.FitFeedback.SMALL, CONFIRMED_AT);

        item.confirm(OrderItem.ConfirmType.AUTO, null, CONFIRMED_AT.plusDays(14));

        assertThat(item.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
        assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        assertThat(item.getFitFeedback()).isEqualTo(OrderItem.FitFeedback.SMALL);
    }
}
