package com.bbcc.kidly.boundedcontext.order.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 순환 커서(#656). 대상은 ID 집합이고 조회는 "afterId 다음부터 ID 오름차순 limit건"이다. 처리한 대상은 지우지 않는다
 * (배송 중 · 계속 실패처럼 다음 실행에도 남는 경우).
 */
class RotatingCursorTest {

    private final List<Long> fetchedAfter = new ArrayList<>();
    private final List<Long> processed = new ArrayList<>();

    private RotatingCursor.Run run(RotatingCursor cursor, Set<Long> ids, int batchSize, int maxPerRun) {
        TreeSet<Long> targets = new TreeSet<>(ids);
        return cursor.run(batchSize, maxPerRun, (afterId, limit) -> {
            fetchedAfter.add(afterId);
            return targets.tailSet(afterId, false).stream().limit(limit).toList();
        }, id -> id, id -> processed.add(id));
    }

    private static Set<Long> ids(long from, long to) {
        TreeSet<Long> ids = new TreeSet<>();
        for (long id = from; id <= to; id++) {
            ids.add(id);
        }
        return ids;
    }

    @Test
    @DisplayName("상한 안에서 끝나면 처음(0)부터 읽고 끝나며 다음 실행도 처음부터: 조회 · 결과가 커서 없을 때와 같다")
    void underCapIsUnchanged() {
        RotatingCursor cursor = new RotatingCursor();

        RotatingCursor.Run first = run(cursor, ids(1, 5), 2, 10);
        RotatingCursor.Run second = run(cursor, ids(1, 5), 2, 10);

        assertThat(first).isEqualTo(new RotatingCursor.Run(5, 0, 3, false));
        assertThat(second).isEqualTo(first);
        assertThat(fetchedAfter).containsExactly(0L, 2L, 4L, 0L, 2L, 4L);
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("상한에 닿으면 멈춘 ID를 기억하고, 다음 실행은 그 다음부터 이어 읽는다(앞쪽이 남아 있어도 뒤쪽 차례가 온다)")
    void continuesAfterCap() {
        RotatingCursor cursor = new RotatingCursor();

        RotatingCursor.Run first = run(cursor, ids(1, 6), 2, 4);

        assertThat(first.capped()).isTrue();
        assertThat(cursor.resumeAfterId()).isEqualTo(4L);
        assertThat(processed).containsExactly(1L, 2L, 3L, 4L);

        processed.clear();
        run(cursor, ids(1, 6), 2, 4);

        assertThat(processed).startsWith(5L, 6L);
    }

    @Test
    @DisplayName("끝에 닿으면 같은 실행에서 처음으로 돌아가 이번 시작 ID까지만 읽는다(두 번 처리 없음). 한 바퀴를 다 돌면 다음은 처음부터")
    void wrapsToStartWithinRun() {
        RotatingCursor cursor = new RotatingCursor();
        run(cursor, ids(1, 6), 2, 4);
        processed.clear();
        fetchedAfter.clear();

        RotatingCursor.Run second = run(cursor, ids(1, 5), 2, 10);

        assertThat(processed).containsExactly(5L, 1L, 2L, 3L, 4L);
        // 처음으로 돌아간 뒤 배치가 시작 ID(4)에서 끝나면 더 조회하지 않는다
        assertThat(fetchedAfter).containsExactly(4L, 0L, 2L);
        assertThat(second).isEqualTo(new RotatingCursor.Run(5, 0, 3, false));
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("처음으로 돌아간 뒤 시작 ID 뒤의 대상(이번 실행에서 이미 읽은 것)은 처리하지 않는다")
    void stopsAtStartAfterWrap() {
        RotatingCursor cursor = new RotatingCursor();
        run(cursor, ids(1, 4), 2, 2);
        processed.clear();

        run(cursor, Set.of(1L, 3L, 4L, 9L), 3, 10);

        assertThat(processed).containsExactly(3L, 4L, 9L, 1L);
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("처음으로 돌아간 뒤에도 상한에 닿으면 거기서 멈추고 그 ID를 기억한다")
    void capsAfterWrap() {
        RotatingCursor cursor = new RotatingCursor();
        run(cursor, ids(1, 8), 2, 6);
        processed.clear();

        RotatingCursor.Run second = run(cursor, ids(1, 8), 2, 6);

        assertThat(processed).containsExactly(7L, 8L, 1L, 2L, 3L, 4L);
        assertThat(second.capped()).isTrue();
        assertThat(cursor.resumeAfterId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("처리해도 빠지지 않는 대상이 상한보다 많아도 ⌈대상 ÷ 상한⌉ 실행 안에 모두 한 번씩 처리된다")
    void everyTargetGetsTurn() {
        RotatingCursor cursor = new RotatingCursor();

        for (int i = 0; i < 3; i++) {
            run(cursor, ids(1, 25), 4, 10);
        }

        assertThat(new TreeSet<>(processed)).isEqualTo(ids(1, 25));
    }

    @Test
    @DisplayName("대상이 0개면 한 번 조회하고 끝나며 상한 도달이 아니고 커서는 처음 그대로")
    void noTargets() {
        RotatingCursor cursor = new RotatingCursor();

        RotatingCursor.Run run = run(cursor, Set.of(), 2, 4);

        assertThat(run).isEqualTo(new RotatingCursor.Run(0, 0, 1, false));
        assertThat(fetchedAfter).containsExactly(0L);
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("대상 수가 정확히 상한이면 남은 대상 확인(1건 조회 더)에서 없음을 보고 상한 도달이 아니며 커서는 처음으로: "
        + "실행마다 같은 결과(상한 도달이 한 실행 걸러 반복되지 않는다)")
    void exactlyCapIsNotCapped() {
        RotatingCursor cursor = new RotatingCursor();

        RotatingCursor.Run first = run(cursor, ids(1, 4), 2, 4);
        RotatingCursor.Run second = run(cursor, ids(1, 4), 2, 4);

        assertThat(first).isEqualTo(new RotatingCursor.Run(4, 0, 2, false));
        assertThat(second).isEqualTo(first);
        assertThat(fetchedAfter).containsExactly(0L, 2L, 4L, 0L, 2L, 4L);
        assertThat(processed).containsExactly(1L, 2L, 3L, 4L, 1L, 2L, 3L, 4L);
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("처음으로 돌아온 뒤 상한에 닿았는데 남은 대상이 시작 ID 뒤뿐이면(이번 실행에서 이미 읽음) 상한 도달이 아니고 커서는 처음으로")
    void exactlyCapAfterWrap() {
        RotatingCursor cursor = new RotatingCursor();
        run(cursor, ids(1, 6), 2, 4);
        processed.clear();

        RotatingCursor.Run second = run(cursor, ids(1, 6), 2, 6);

        assertThat(processed).containsExactly(5L, 6L, 1L, 2L, 3L, 4L);
        assertThat(second.capped()).isFalse();
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("멈춘 ID(시작 ID)의 대상이 그사이 사라져도 처음으로 돌아온 뒤 그보다 큰 ID에서 멈춘다(두 번 처리 없음)")
    void startIdRemoved() {
        RotatingCursor cursor = new RotatingCursor();
        run(cursor, ids(1, 6), 2, 4);
        processed.clear();

        run(cursor, Set.of(1L, 2L, 3L, 5L, 6L), 2, 10);

        assertThat(processed).containsExactly(5L, 6L, 1L, 2L, 3L);
        assertThat(cursor.resumeAfterId()).isZero();
    }

    @Test
    @DisplayName("처리하면 대상에서 빠지는 작업(자동 구매확정 성공 등)도 상한에서 이어 읽고, 다 처리되면 처음으로 돌아가 빈 조회로 끝난다")
    void processedTargetsDisappear() {
        RotatingCursor cursor = new RotatingCursor();
        TreeSet<Long> remaining = new TreeSet<>(ids(1, 6));
        Supplier<RotatingCursor.Run> runOnce = () -> cursor.run(2, 4, (afterId, limit) -> {
            fetchedAfter.add(afterId);
            return remaining.tailSet(afterId, false).stream().limit(limit).toList();
        }, id -> id, id -> processed.add(id) && remaining.remove(id));

        RotatingCursor.Run first = runOnce.get();
        RotatingCursor.Run second = runOnce.get();

        assertThat(first.capped()).isTrue();
        assertThat(second).isEqualTo(new RotatingCursor.Run(2, 0, 3, false));
        assertThat(processed).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(remaining).isEmpty();
        // 둘째 실행: 4 다음(5 · 6) → 끝 → 처음으로(빈 조회)
        assertThat(fetchedAfter).containsExactly(0L, 2L, 4L, 4L, 6L, 0L);
        assertThat(cursor.resumeAfterId()).isZero();
    }
}
