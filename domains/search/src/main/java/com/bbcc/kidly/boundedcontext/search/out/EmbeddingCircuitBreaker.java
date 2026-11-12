package com.bbcc.kidly.boundedcontext.search.out;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * 임베딩 API가 연속으로 실패하면 잠시 부르지 않는다(#797 리뷰).
 * 검색 요청이 API를 기다리므로, 장애 동안 요청마다 시간 초과(최대 약 3초)를 기다리지 않고 바로 키워드로만 찾게 한다.
 * 연속 {@value #FAILURE_THRESHOLD}번 실패하면 열고, {@link #OPEN_DURATION}이 지나면 호출 하나만 시험으로 보낸다.
 * 시험이 성공하면 닫고, 실패하면 다시 그만큼 연다. 상태 전환 때만 로그를 남긴다.
 * 바꿀 일이 드물어 설정으로 빼지 않는다.
 */
@Slf4j
final class EmbeddingCircuitBreaker {

    static final int FAILURE_THRESHOLD = 5;
    static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    enum Permit {
        /** 닫혀 있어 부른다 */
        CALL,
        /** 열린 뒤 시간이 지나 시험으로 부른다(한 번에 하나) */
        PROBE,
        /** 열려 있어 부르지 않는다 */
        REJECTED
    }

    private final Clock clock;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean probing;

    EmbeddingCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    synchronized Permit tryAcquire() {
        if (openedAt == null) {
            return Permit.CALL;
        }
        if (probing || clock.instant().isBefore(openedAt.plus(OPEN_DURATION))) {
            return Permit.REJECTED;
        }
        probing = true;
        return Permit.PROBE;
    }

    // 열려 있으면(시험 호출 중 포함) true. 지표(kidly.search.embedding.circuit.open)가 읽는다
    synchronized boolean isOpen() {
        return openedAt != null;
    }

    synchronized void onSuccess(Permit permit) {
        consecutiveFailures = 0;
        if (permit == Permit.PROBE) {
            probing = false;
        }
        if (openedAt != null) {
            openedAt = null;
            log.info("임베딩 API가 다시 응답해 의미 검색을 다시 씁니다");
        }
    }

    synchronized void onFailure(Permit permit) {
        if (permit == Permit.PROBE) {
            probing = false;
            openedAt = clock.instant();
            return;
        }
        // 열리기 전에 나가 열린 뒤에 끝난 호출은 세지 않는다
        if (openedAt != null) {
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            openedAt = clock.instant();
            log.warn("임베딩 API가 연속 {}번 실패해 {}초 동안 부르지 않습니다. 그동안 검색은 키워드로만 찾습니다",
                consecutiveFailures, OPEN_DURATION.toSeconds());
        }
    }
}
