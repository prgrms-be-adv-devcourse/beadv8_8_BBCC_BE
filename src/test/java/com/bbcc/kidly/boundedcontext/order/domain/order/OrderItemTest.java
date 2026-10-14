package com.bbcc.kidly.boundedcontext.order.domain.order;

import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.DELIVERED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_A_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.SELLER_B_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.order.OrderFixture.itemOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 구매확정 규칙. 직접 · 자동 모두 같은 confirm 경로를 쓴다.
 */
class OrderItemTest {

    private static final LocalDateTime CONFIRMED_AT = DELIVERED_AT.plusDays(1);

    @Nested
    @DisplayName("구매확정")
    class Confirm {

        @Test
        @DisplayName("배송 완료된 주문상품은 확정 시각 · 방식을 남기고 true")
        void confirmsDelivered() {
            OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_A_ID);

            boolean confirmed = item.confirm(OrderItem.ConfirmType.DIRECT, CONFIRMED_AT);

            assertThat(confirmed).isTrue();
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.CONFIRMED);
            assertThat(item.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        }

        @Test
        @DisplayName("배송 완료 전이면 그대로 두고 false")
        void notDelivered() {
            OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_B_ID);

            boolean confirmed = item.confirm(OrderItem.ConfirmType.DIRECT, CONFIRMED_AT);

            assertThat(confirmed).isFalse();
            assertThat(item.getStatus()).isEqualTo(OrderItem.Status.ORDERED);
            assertThat(item.getConfirmedAt()).isNull();
        }

        @Test
        @DisplayName("이미 확정이면 처음 값을 유지하고 false (직접 · 자동이 겹쳐도 한 번)")
        void confirmOnlyOnce() {
            OrderItem item = itemOf(OrderFixture.deliveredToSellerA(), SELLER_A_ID);
            item.confirm(OrderItem.ConfirmType.DIRECT, CONFIRMED_AT);

            boolean confirmed = item.confirm(OrderItem.ConfirmType.AUTO, CONFIRMED_AT.plusDays(14));

            assertThat(confirmed).isFalse();
            assertThat(item.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
            assertThat(item.getConfirmType()).isEqualTo(OrderItem.ConfirmType.DIRECT);
        }
    }
}
