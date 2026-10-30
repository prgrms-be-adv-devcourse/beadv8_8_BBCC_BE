package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDate;
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

    // 다음 실행이 찾기 시작할 id. 계속 결과를 못 받는 정산서가 앞쪽을 차지해도 뒤쪽 차례가 오게 돌아가며 찾는다.
    // 서버마다 따로 두고 재시작하면 처음부터 찾는다. 같은 정산서를 다시 요청해도 결제가 한 번만 입금한다
    private long nextAfterId = 0L;
    // 7일 넘은 정산서 경고를 마지막으로 남긴 날. 같은 경고가 실행마다 반복되지 않게 하루 한 번만 남긴다
    private LocalDate lastExpiredWarnedOn;

    @Scheduled(cron = "${kidly.settlement.payout-retry-cron}", zone = "Asia/Seoul")
    public void retryStuck() {
        retryStuckPayouts(LocalDateTime.now());
    }

    /**
     * 다시 요청한 건수를 돌려준다. 한 건이 실패해도 나머지는 계속한다.
     */
    public int retryStuckPayouts(LocalDateTime now) {
        var stuck = settlementRepository.findByStatusAndCompletedAtBetweenAndIdGreaterThanOrderByIdAsc(
            SettlementStatus.COMPLETED, now.minus(RETRY_WINDOW), now.minus(RETRY_AFTER), nextAfterId,
            Limit.of(BATCH_SIZE));
        // 한 번에 다 채우지 못했으면 끝에 닿은 것이라 다음 실행은 처음부터 찾는다
        nextAfterId = stuck.size() < BATCH_SIZE ? 0L : stuck.getLast().getId();
        int requested = 0;
        for (var settlement : stuck) {
            if (retry(settlement.getId())) {
                requested++;
            }
        }
        if (requested > 0) {
            log.info("지급 결과를 받지 못한 정산서 {}건에 지급을 다시 요청했습니다", requested);
        }
        warnExpiredOncePerDay(now);
        return requested;
    }

    private void warnExpiredOncePerDay(LocalDateTime now) {
        var today = now.toLocalDate();
        if (today.equals(lastExpiredWarnedOn)) {
            return;
        }
        long expired = settlementRepository.countByStatusAndCompletedAtBefore(
            SettlementStatus.COMPLETED, now.minus(RETRY_WINDOW));
        if (expired > 0) {
            log.warn("7일 넘게 지급 결과를 받지 못한 정산서 {}건. 관리자 재요청이 필요합니다", expired);
            lastExpiredWarnedOn = today;
        }
    }

    private boolean retry(Long settlementId) {
        try {
            adminSettlementService.retryPayout(settlementId);
            return true;
        } catch (RuntimeException e) {
            if (e instanceof BusinessException b
                && b.getErrorCode() == SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE) {
                log.debug("이미 지급 결과를 받은 정산서라 건너뜁니다. settlementId={}", settlementId);
            } else {
                log.error("정산서 지급 재요청에 실패했습니다. settlementId={}", settlementId, e);
            }
            return false;
        }
    }
}
