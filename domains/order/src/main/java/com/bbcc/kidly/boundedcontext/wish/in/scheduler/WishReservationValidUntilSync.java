package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import com.bbcc.kidly.boundedcontext.wish.app.WishReservationValidUntilService;
import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishJobRunner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 앱이 뜰 때 한 번, 진행 중 조르기의 장바구니 예약 결제 가능 기한을 지금 만료 설정(ttl · 켜짐)으로 다시 맞춘다(#1008).
 * 만료 설정은 기동 때만 바뀌므로 주기마다 돌지 않는다. 만료가 꺼져 있어도 돈다(기한을 없앤다 = null): 그래서 만료 주기 작업
 * (WishExpiryScheduler, 만료가 꺼지면 빈이 없다)과 따로 둔다. 서버가 여러 대면 만료 작업과 같은 잠금(WishJobRunner, 잠금 키
 * kidly.order.wish-valid-until)으로 한 대에서만 돌고, 동시에 뜬 다른 서버는 건너뛴다(값이 같아 결과도 같다).
 * 배치가 결제 정리 · 예약 해제와 겹쳐 낙관적 락으로 지면(장바구니 예약 @Version) 그 배치만 한 건씩 다시 맞추고,
 * 또 겹치는 건은 건너뛴다(DEBUG, 다음 기동에서 다시 · 해제된 예약은 맞출 것이 없다).
 * 실패해도 기동은 막지 않는다(WARN). 그 경우 예약 기한은 다음 기동까지 예전 계산값이고, 만료 작업 · 조르기 409 판정은 지금 설정으로
 * 계산하므로 영향이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishReservationValidUntilSync {

    static final String JOB = "wish-valid-until";
    static final int BATCH_SIZE = 100;

    private final WishReservationValidUntilService service;
    private final WishExpiryProperties properties;
    private final WishJobRunner jobRunner;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            jobRunner.run(JOB, this::syncAll);
        } catch (RuntimeException e) {
            log.warn("조르기 예약 기한 맞추기 실패: job={} exception={}", JOB, e.getClass().getSimpleName());
        }
    }

    /**
     * 진행 중 조르기 전부를 ID 순으로 배치마다 맞춘다.
     *
     * @return 실제로 기한이 바뀐 예약 수
     */
    int syncAll() {
        long read = 0;
        int changed = 0;
        Long afterId = 0L;
        while (true) {
            WishReservationValidUntilService.Batch batch;
            try {
                batch = service.syncBatch(afterId, BATCH_SIZE);
            } catch (OptimisticLockingFailureException e) {
                batch = syncEach(afterId);
            }
            read += batch.read();
            changed += batch.changed();
            if (batch.read() < BATCH_SIZE) {
                break;
            }
            afterId = batch.lastId();
        }
        log.info("조르기 예약 기한 맞춤: job={} requests={} changed={} enabled={} ttl={}", JOB, read, changed,
            properties.enabled(), properties.ttl());
        return changed;
    }

    // 배치 하나를 조르기마다 자기 트랜잭션으로 다시 맞춘다. 또 겹치는 건은 건너뛴다
    private WishReservationValidUntilService.Batch syncEach(Long afterId) {
        List<Long> ids = service.requestIds(afterId, BATCH_SIZE);
        int changed = 0;
        for (Long requestId : ids) {
            try {
                changed += service.syncOne(requestId);
            } catch (OptimisticLockingFailureException e) {
                log.debug("조르기 예약 기한 맞추기를 건너뜁니다(동시 변경): job={} requestId={}", JOB, requestId);
            }
        }
        return new WishReservationValidUntilService.Batch(ids.size(), ids.isEmpty() ? afterId : ids.getLast(), changed);
    }
}
