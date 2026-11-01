package com.bbcc.kidly.boundedcontext.delivery.in.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 주기 작업의 순환 ID 커서(#656). 한 실행은 지난 실행이 상한(max-per-run)에 닿아 멈춘 ID 다음부터 batch-size건씩 이어 읽고,
 * 끝에 닿으면 같은 실행 안에서 처음(ID 0)으로 돌아가 이번 실행의 시작 ID까지 읽는다. 한 바퀴를 다 돌면 다음 실행은 처음부터다.
 * 그래서 처리해도 대상에서 빠지지 않는 건(계속 실패 등)이 상한 이상 앞쪽에 쌓여도 뒤쪽이 몇 실행 안에 차례가 온다.
 * 커서는 메모리에 둔다(작업마다 하나, 서버마다 따로, 재기동하면 처음부터). 한 작업은 한 번에 한 스레드만 부른다(잠금 실행).
 * 여러 서버가 번갈아 잠금을 잡고 재기동도 잦으면 뒤쪽 차례가 늦어질 수 있다(그때는 상한 도달 WARN으로 드러난다).
 * 상한에 닿으면 한 건을 더 조회해 이번 바퀴에 남은 대상이 실제로 있을 때만 멈춘 것으로 본다(대상 수가 정확히 상한이면 멈춤 아님 ·
 * 커서는 처음으로). 그 한 번의 조회는 상한에 닿은 실행에만 더해진다.
 * 상한 안에서 끝나는 실행은 늘 처음부터 읽으므로 조회 · 결과가 커서 없을 때와 같다.
 * 주문 컨텍스트 OrderScheduler의 것과 같은 코드다(컨텍스트 사이에는 코드를 나누지 않는다, #617 · #641).
 */
final class RotatingCursor {

    // 다음 실행이 이어 읽을 위치(이 ID 다음부터). 0이면 처음부터
    private volatile long resumeAfterId;

    long resumeAfterId() {
        return resumeAfterId;
    }

    /**
     * @param fetch afterId 다음부터 ID 오름차순 limit건
     * @param process 대상 하나를 처리한다. 성공이면 true, 실패(로그를 남긴 뒤)면 false
     */
    <T> Run run(int batchSize, int maxPerRun, BiFunction<Long, Integer, List<T>> fetch, Function<T, Long> idOf,
        Predicate<T> process) {
        long start = resumeAfterId;
        long afterId = start;
        boolean wrapped = false;
        int targets = 0;
        int failed = 0;
        int batches = 0;
        while (targets < maxPerRun) {
            int limit = Math.min(batchSize, maxPerRun - targets);
            List<T> batch = fetch.apply(afterId, limit);
            batches++;
            boolean reachedStart = false;
            if (wrapped) {
                // 처음으로 돌아온 뒤에는 이번 실행의 시작 ID까지만(그 뒤는 이번 실행에서 이미 읽었다)
                List<T> beforeStart = new ArrayList<>();
                for (T target : batch) {
                    if (idOf.apply(target) > start) {
                        reachedStart = true;
                        break;
                    }
                    beforeStart.add(target);
                }
                reachedStart = reachedStart || (!batch.isEmpty() && idOf.apply(batch.getLast()) == start);
                batch = beforeStart;
            }
            for (T target : batch) {
                if (!process.test(target)) {
                    failed++;
                }
            }
            targets += batch.size();
            if (reachedStart || batch.size() < limit) {
                if (!wrapped && start > 0 && !reachedStart && targets < maxPerRun) {
                    wrapped = true;
                    afterId = 0L;
                    continue;
                }
                resumeAfterId = 0L;
                return new Run(targets, failed, batches, false);
            }
            afterId = idOf.apply(batch.getLast());
        }
        // 상한에 닿았다: 이번 바퀴에 남은 대상이 있는지 한 건만 더 본다(처음으로 돌아온 뒤면 시작 ID까지만 남은 대상)
        List<T> next = fetch.apply(afterId, 1);
        boolean remaining = !next.isEmpty() && (!wrapped || idOf.apply(next.getFirst()) <= start);
        if (!remaining) {
            resumeAfterId = 0L;
            return new Run(targets, failed, batches, false);
        }
        resumeAfterId = afterId;
        return new Run(targets, failed, batches, true);
    }

    /**
     * @param capped 상한에 닿았고 이번 바퀴에 남은 대상이 있어 멈췄으면 true(다음 실행이 이어 읽는다)
     * @param batches 처리할 대상을 읽은 조회 수(상한에서 남은 대상을 확인한 한 번은 세지 않는다)
     */
    record Run(int targets, int failed, int batches, boolean capped) {
    }
}
