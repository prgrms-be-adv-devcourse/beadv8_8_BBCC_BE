package com.bbcc.kidly.boundedcontext.settlement.app.outbox;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 정산 아웃박스 릴레이 · 정리 설정. 기본값은 settlement.yml 한 곳에만 둔다. 정리 시각(cleanup-cron)은 @Scheduled가 읽는다.
 * 스프링은 1초 미만 양수 주기를 그대로 받으므로, 여기서 1초 이상을 검증해 키 이름이 나오는 바인딩 오류로 기동에 실패하게 한다.
 *
 * @param relayInterval 릴레이 주기(이전 실행이 끝난 뒤부터 잰다). 1초 이상
 * @param batchSize 한 번 실행에서 읽어 보내는 최대 건수
 * @param retention 보낸 메시지를 남겨 두는 기간. 지나면 정리가 지운다. 1초 이상
 */
@Validated
@ConfigurationProperties("kidly.settlement.outbox")
public record SettlementOutboxProperties(
    @NotNull Duration relayInterval,
    @NotNull @Positive Integer batchSize,
    @NotNull Duration retention) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.settlement.outbox.relay-interval은 1초 이상이어야 합니다")
    public boolean isRelayIntervalAtLeastOneSecond() {
        return relayInterval == null || relayInterval.compareTo(MIN) >= 0;
    }

    @AssertTrue(message = "kidly.settlement.outbox.retention은 1초 이상이어야 합니다")
    public boolean isRetentionAtLeastOneSecond() {
        return retention == null || retention.compareTo(MIN) >= 0;
    }
}
