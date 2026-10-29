package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지급 결과를 받지 못한 정산서에 지급을 다시 요청한다. 메모리 이벤트를 잃었을 때(서버 재시작 등) 복구하는 장치다.
 * 결제가 같은 정산서 ID로 한 번만 입금하므로 서버가 여러 대여도 잠금 없이 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutRetryScheduler {

    // 이 시간이 지나도 지급 결과가 없으면 다시 요청한다
    private static final Duration RETRY_AFTER = Duration.ofMinutes(30);
    // 이 기간이 지난 건은 자동으로 다시 요청하지 않고 관리자에게 맡긴다
    private static final Duration RETRY_WINDOW = Duration.ofDays(7);
    // 한 번에 다시 요청하는 최대 건수
    private static final int BATCH_SIZE = 100;

    private final SettlementRepository settlementRepository;
    private final AdminSettlementService adminSettlementService;

    @Scheduled(cron = "${kidly.settlement.payout-retry-cron}", zone = "Asia/Seoul")
    public void retryStuck() {
        retryStuckPayouts(LocalDateTime.now());
    }

    /**
     * 다시 요청한 건수를 돌려준다. 한 건이 실패해도 나머지는 계속한다.
     */
    public int retryStuckPayouts(LocalDateTime now) {
        var stuck = settlementRepository.findByStatusAndCompletedAtBetweenOrderByIdAsc(
            SettlementStatus.COMPLETED, now.minus(RETRY_WINDOW), now.minus(RETRY_AFTER), Limit.of(BATCH_SIZE));
        int requested = 0;
        for (var settlement : stuck) {
            if (retry(settlement.getId())) {
                requested++;
            }
        }
        if (requested > 0) {
            log.info("지급 결과를 받지 못한 정산서 {}건에 지급을 다시 요청했습니다", requested);
        }
        long expired = settlementRepository.countByStatusAndCompletedAtBefore(
            SettlementStatus.COMPLETED, now.minus(RETRY_WINDOW));
        if (expired > 0) {
            log.warn("7일 넘게 지급 결과를 받지 못한 정산서 {}건. 관리자 재요청이 필요합니다", expired);
        }
        return requested;
    }

    private boolean retry(Long settlementId) {
        try {
            adminSettlementService.retryPayout(settlementId);
            return true;
        } catch (BusinessException e) {
            if (e.getErrorCode() == SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE) {
                log.debug("이미 지급 결과를 받은 정산서라 건너뜁니다. settlementId={}", settlementId);
            } else {
                log.error("정산서 지급 재요청에 실패했습니다. settlementId={}", settlementId, e);
            }
            return false;
        } catch (RuntimeException e) {
            log.error("정산서 지급 재요청에 실패했습니다. settlementId={}", settlementId, e);
            return false;
        }
    }
}
