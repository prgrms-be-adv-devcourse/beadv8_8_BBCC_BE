package com.bbcc.kidly.boundedcontext.delivery.app;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 택배 조회 스케줄러 설정. 기본값은 order.yml 한 곳에만 둔다. @Scheduled는 interval 키를 문자열로 읽으므로,
 * 주문 OrderScheduleIntervals와 같은 이유로 1초 이상을 여기서 검증해 키 이름이 나오는 바인딩 오류로 기동에 실패하게 한다.
 * 주문 컨텍스트의 건수 설정(kidly.order.scheduler)은 다른 컨텍스트라 쓰지 않고 따로 둔다.
 *
 * @param interval 주기. 1초 이상(이전 실행이 끝난 뒤부터 잰다)
 * @param batchSize 한 번 읽는 배송 수
 * @param maxPerRun 한 실행에서 조회하는 최대 배송 수. batchSize 이상
 */
@Validated
@ConfigurationProperties("kidly.order.delivery-tracking")
public record DeliveryTrackingProperties(
    @NotNull Duration interval,
    @NotNull @Positive Integer batchSize,
    @NotNull @Positive Integer maxPerRun) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.order.delivery-tracking.interval은 1초 이상이어야 합니다")
    public boolean isIntervalAtLeastOneSecond() {
        return interval == null || interval.compareTo(MIN) >= 0;
    }

    @AssertTrue(message = "kidly.order.delivery-tracking.max-per-run은 batch-size 이상이어야 합니다")
    public boolean isMaxPerRunAtLeastBatchSize() {
        return batchSize == null || maxPerRun == null || maxPerRun >= batchSize;
    }
}
