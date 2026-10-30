package com.bbcc.kidly.boundedcontext.settlement.app;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * 정산 설정. 기본값은 settlement.yml에 있다.
 *
 * @param feeRate 수수료율. 0 이상 1 이하, 소수점 넷째 자리까지
 * @param runCron 자동 정산 cron (Asia/Seoul)
 * @param payoutRetryCron 지급 결과를 못 받은 정산서를 다시 요청하는 cron (Asia/Seoul)
 * @param chunkSize 한 트랜잭션에서 처리하는 판매자 수
 */
@ConfigurationProperties("kidly.settlement")
public record SettlementProperties(BigDecimal feeRate, String runCron, String payoutRetryCron, int chunkSize) {

    public SettlementProperties {
        requireNonNull(feeRate, "kidly.settlement.fee-rate는 필수입니다");
        Settlement.validateFeeRate(feeRate);
        Assert.hasText(runCron, "kidly.settlement.run-cron은 필수입니다");
        Assert.hasText(payoutRetryCron, "kidly.settlement.payout-retry-cron은 필수입니다");
        Assert.isTrue(chunkSize >= 1, "kidly.settlement.chunk-size는 1 이상이어야 합니다");
    }
}
