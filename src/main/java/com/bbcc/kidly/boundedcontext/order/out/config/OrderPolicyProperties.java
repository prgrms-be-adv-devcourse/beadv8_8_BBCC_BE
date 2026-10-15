package com.bbcc.kidly.boundedcontext.order.out.config;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 주문 설정값(주문 정리 9장). 기본값과 환경변수 이름은 order.yml 한 곳에만 둔다.
 * 값이 빠지면 키 이름이 나오는 바인딩 오류로 기동에 실패한다.
 *
 * @param paymentTimeout 결제 마감
 */
@Validated
@ConfigurationProperties("kidly.order")
public record OrderPolicyProperties(
    @NotNull Duration paymentTimeout,
    @NotNull AutoConfirm autoConfirm) {

    /**
     * 칸이 after 하나뿐이라 after가 없으면 autoConfirm 자체가 null이 되어 위 @NotNull에 걸린다.
     *
     * @param after 배송 완료 후 자동 구매확정까지
     */
    public record AutoConfirm(Duration after) {
    }

    public Order.Policy toOrderPolicy() {
        return new Order.Policy(paymentTimeout, autoConfirm.after());
    }
}
