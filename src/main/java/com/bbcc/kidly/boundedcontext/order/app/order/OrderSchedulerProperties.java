package com.bbcc.kidly.boundedcontext.order.app.order;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 주문 스케줄러(결제 마감 만료 · 자동 구매확정)가 한 번에 읽는 건수와 한 실행의 상한. 기본값은 order.yml 한 곳에만 둔다.
 * 값이 빠졌거나 1보다 작거나 상한이 한 번 건수보다 작으면 키 이름이 나오는 바인딩 오류로 기동에 실패한다.
 *
 * @param batchSize 한 번 조회하는 건수
 * @param maxPerRun 한 실행에서 처리하는 최대 건수. batchSize 이상
 */
@Validated
@ConfigurationProperties("kidly.order.scheduler")
public record OrderSchedulerProperties(
    @NotNull @Positive Integer batchSize,
    @NotNull @Positive Integer maxPerRun) {

    @AssertTrue(message = "kidly.order.scheduler.max-per-run은 kidly.order.scheduler.batch-size 이상이어야 합니다")
    public boolean isMaxPerRunAtLeastBatchSize() {
        return batchSize == null || maxPerRun == null || maxPerRun >= batchSize;
    }
}
