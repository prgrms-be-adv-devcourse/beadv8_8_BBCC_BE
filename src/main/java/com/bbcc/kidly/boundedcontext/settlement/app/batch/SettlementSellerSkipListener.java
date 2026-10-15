package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;

/**
 * 건너뛴 판매자를 남긴다. 그 판매자의 항목은 대기로 남아 다음 정산에서 다시 계산된다.
 */
@Slf4j
public class SettlementSellerSkipListener implements SkipListener<Long, Long> {

    @Override
    public void onSkipInWrite(Long sellerId, Throwable t) {
        log.error("판매자 {}의 정산을 건너뜁니다", sellerId, t);
    }
}
