package com.bbcc.kidly.boundedcontext.order.out.config;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 주문 설정값(주문 정리 9장). yml에 없으면 기본값을 쓴다. 시연 프로필에서 시간을 줄인다.
 *
 * @param paymentTimeout 결제 마감
 */
@ConfigurationProperties("kidly.order")
public record OrderPolicyProperties(
    @DefaultValue("PT10M") Duration paymentTimeout,
    @DefaultValue AutoConfirm autoConfirm) {

    /**
     * @param after 배송 완료 후 자동 구매확정까지
     */
    public record AutoConfirm(@DefaultValue("P15D") Duration after) {
    }

    public Order.Policy toOrderPolicy() {
        return new Order.Policy(paymentTimeout, autoConfirm.after());
    }
}
