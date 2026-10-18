package com.bbcc.kidly.boundedcontext.order.app.stuck;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 멈춘 주문으로 보기까지 기다리는 시간(#445). 기본값은 order.yml 한 곳에만 둔다.
 * 값이 빠지거나 1초 미만(0 · 음수 포함)이면 키 이름이 나오는 바인딩 오류로 기동에 실패한다.
 *
 * @param stockWait 주문하기 뒤 결제 요청(재고 차감 완료)까지
 * @param expiryGrace 결제 마감 뒤 만료 취소까지
 * @param deliveryWait 결제완료 뒤 판매자별 배송 생성까지
 * @param recoveryWait 결제 반영 실패 기록 뒤 관리자 처리까지
 * @param shippingWait 배송 준비 뒤 발송까지
 * @param autoConfirmGrace 자동 구매확정 기간이 지난 뒤 확정까지
 */
@Validated
@ConfigurationProperties("kidly.order.stuck")
public record StuckOrderThresholds(
    @NotNull Duration stockWait,
    @NotNull Duration expiryGrace,
    @NotNull Duration deliveryWait,
    @NotNull Duration recoveryWait,
    @NotNull Duration shippingWait,
    @NotNull Duration autoConfirmGrace) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.order.stuck.stock-wait은 1초 이상이어야 합니다")
    public boolean isStockWaitAtLeastOneSecond() {
        return atLeastOneSecond(stockWait);
    }

    @AssertTrue(message = "kidly.order.stuck.expiry-grace는 1초 이상이어야 합니다")
    public boolean isExpiryGraceAtLeastOneSecond() {
        return atLeastOneSecond(expiryGrace);
    }

    @AssertTrue(message = "kidly.order.stuck.delivery-wait은 1초 이상이어야 합니다")
    public boolean isDeliveryWaitAtLeastOneSecond() {
        return atLeastOneSecond(deliveryWait);
    }

    @AssertTrue(message = "kidly.order.stuck.recovery-wait은 1초 이상이어야 합니다")
    public boolean isRecoveryWaitAtLeastOneSecond() {
        return atLeastOneSecond(recoveryWait);
    }

    @AssertTrue(message = "kidly.order.stuck.shipping-wait은 1초 이상이어야 합니다")
    public boolean isShippingWaitAtLeastOneSecond() {
        return atLeastOneSecond(shippingWait);
    }

    @AssertTrue(message = "kidly.order.stuck.auto-confirm-grace는 1초 이상이어야 합니다")
    public boolean isAutoConfirmGraceAtLeastOneSecond() {
        return atLeastOneSecond(autoConfirmGrace);
    }

    // 빈 값은 @NotNull이 키 이름과 함께 잡으므로 여기서는 통과시킨다
    private static boolean atLeastOneSecond(Duration value) {
        return value == null || value.compareTo(MIN) >= 0;
    }
}
