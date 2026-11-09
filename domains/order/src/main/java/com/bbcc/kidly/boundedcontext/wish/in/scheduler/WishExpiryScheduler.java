package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import com.bbcc.kidly.boundedcontext.wish.app.WishExpiryService;
import com.bbcc.kidly.boundedcontext.wish.app.WishJobMetrics;
import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishJobRunner;
import java.time.LocalDateTime;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기한 + 유예가 지난 대기 조르기를 만료한다(#1008, BEG-05). 주기는 kidly.order.wish.expiry.interval(기본값은 order.yml).
 * 자동 구매확정(주문 OrderScheduler)과 같은 방식이다: 한 번에 한 서버에서만(WishJobRunner, 잠금 키 kidly.order.wish-expiry),
 * 조르기 ID 커서로 batch-size건씩 이어 읽고, 대상 하나 = 트랜잭션 하나, 한 건이 실패해도 WARN만 남기고 다음 건을 처리한다.
 * 상한에 닿으면 순환 커서({@link RotatingCursor})로 다음 실행이 이어 읽는다. 만료 스위치(kidly.order.wish.expiry.enabled)가
 * 꺼져 있거나 조르기 스위치가 꺼져 있으면 이 빈이 없다(작업이 돌지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBooleanProperty(name = "kidly.order.wish.expiry.enabled", matchIfMissing = true)
public class WishExpiryScheduler {

    static final String JOB = "wish-expiry";
    static final int BATCH_SIZE = 100;
    static final int MAX_PER_RUN = 1000;

    private final WishExpiryService wishExpiryService;
    // 주기 값의 바인딩 검증(1초 이상)이 @Scheduled 등록보다 먼저 돌게 하려고 주입한다(배송 DeliveryStatusPoller와 같음)
    private final WishExpiryProperties properties;
    private final WishJobRunner jobRunner;
    private final WishJobMetrics metrics;
    private final RotatingCursor cursor = new RotatingCursor();

    // 기동 직후가 아니라 한 주기 뒤에 처음 돈다(#926 방식): 배포 직후 여러 서버가 함께 뜨며 몰리지 않게 하고, 테스트 컨텍스트가
    // 뜰 때 저절로 돌지 않게 한다. 그래서 배포 직후 최대 한 주기 동안 예약 해제가 늦을 수 있다(409 판정은 계산이라 영향 없음)
    @Scheduled(fixedDelayString = "${kidly.order.wish.expiry.interval}",
        initialDelayString = "${kidly.order.wish.expiry.interval}")
    public void expireOverdue() {
        // 마지막 성공 시각(#1067): 이 서버가 끝까지 돌았을 때만. 예외로 끝나면 갱신하지 않는다
        if (jobRunner.run(JOB, this::expireTargets)) {
            metrics.markExpirySuccess();
        }
    }

    private void expireTargets() {
        // 기준 시각은 실행마다 한 번만 정해 모든 배치에 같게 쓴다(실행 중 새로 기한이 지난 건은 다음 실행에서)
        LocalDateTime now = LocalDateTime.now();
        RotatingCursor.Run run = cursor.run(BATCH_SIZE, MAX_PER_RUN,
            (afterId, limit) -> wishExpiryService.findOverdueRequestIds(now, afterId, limit), Function.identity(),
            requestId -> {
                try {
                    wishExpiryService.expire(requestId, now);
                    return true;
                } catch (RuntimeException e) {
                    log.warn("조르기 만료 실패: requestId={} exception={}", requestId, e.getClass().getSimpleName());
                    return false;
                }
            });
        if (run.targets() == 0) {
            log.debug("스케줄러 실행: job={} targets=0 interval={}", JOB, properties.interval());
            return;
        }
        if (run.capped()) {
            log.warn("스케줄러 상한 도달: job={} targets={} processed={} failed={} maxPerRun={} resumeAfterId={} interval={}",
                JOB, run.targets(), run.targets() - run.failed(), run.failed(), MAX_PER_RUN, cursor.resumeAfterId(),
                properties.interval());
            return;
        }
        log.info("스케줄러 실행: job={} targets={} processed={} failed={} interval={}", JOB, run.targets(),
            run.targets() - run.failed(), run.failed(), properties.interval());
    }
}
