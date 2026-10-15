package com.bbcc.kidly.boundedcontext.settlement.app;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * 정산 설정. 기본값은 settlement.yml에 있다.
 *
 * @param feeRate 수수료율. 범위 검증은 Settlement가 한다
 * @param runCron 자동 정산 cron (Asia/Seoul)
 * @param chunkSize 한 트랜잭션에서 처리하는 판매자 수
 */
@ConfigurationProperties("kidly.settlement")
public record SettlementProperties(BigDecimal feeRate, String runCron, int chunkSize) {

    public SettlementProperties {
        requireNonNull(feeRate, "kidly.settlement.fee-rate는 필수입니다");
        Assert.hasText(runCron, "kidly.settlement.run-cron은 필수입니다");
        Assert.isTrue(chunkSize >= 1, "kidly.settlement.chunk-size는 1 이상이어야 합니다");
    }
}
