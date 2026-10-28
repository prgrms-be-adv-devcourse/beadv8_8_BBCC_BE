package com.bbcc.kidly.boundedcontext.order.app.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 두 스케줄러의 실행 주기. @Scheduled는 같은 키를 문자열로 읽는다. 기본값은 order.yml 한 곳에만 둔다.
 * 스프링은 0 · 음수 주기를 메시지 없는 오류로 거절하고 1초 미만 양수는 그대로 받으므로,
 * 여기서 1초 이상을 검증해 키 이름이 나오는 바인딩 오류로 기동에 실패하게 한다.
 *
 * @param paymentExpiryInterval 결제 마감 만료 스케줄러 주기. 1초 이상
 * @param autoConfirm 자동 구매확정 스케줄러 주기
 */
@Validated
@ConfigurationProperties("kidly.order")
public record OrderScheduleIntervals(
    @NotNull Duration paymentExpiryInterval,
    @NotNull AutoConfirm autoConfirm) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.order.payment-expiry-interval은 1초 이상이어야 합니다")
    public boolean isPaymentExpiryIntervalAtLeastOneSecond() {
        return paymentExpiryInterval == null || paymentExpiryInterval.compareTo(MIN) >= 0;
    }

    @AssertTrue(message = "kidly.order.auto-confirm.interval은 1초 이상이어야 합니다")
    public boolean isAutoConfirmIntervalAtLeastOneSecond() {
        return autoConfirm == null || autoConfirm.interval() == null || autoConfirm.interval().compareTo(MIN) >= 0;
    }

    /**
     * 칸이 interval 하나뿐이라 interval이 없으면 autoConfirm 자체가 null이 되어 위 @NotNull에 걸린다.
     *
     * @param interval 자동 구매확정 스케줄러 주기. 1초 이상
     */
    public record AutoConfirm(Duration interval) {
    }
}
