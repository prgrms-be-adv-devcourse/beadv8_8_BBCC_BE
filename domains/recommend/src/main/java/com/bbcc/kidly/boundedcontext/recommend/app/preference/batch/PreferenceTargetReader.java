package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * 선호를 계산할 대상 ID를 오름차순으로 한 페이지씩 읽는다.
 * chunk가 커밋될 때마다 마지막으로 돌려준 ID를 저장하므로, 실패한 Step을 다시 실행하면 커밋된 마지막 chunk 다음부터 읽는다.
 */
public class PreferenceTargetReader implements ItemStreamReader<Long> {

    private static final String LAST_TARGET_ID = "lastTargetId";

    private final PreferenceTargets targets;
    private final LocalDateTime baseTime;
    private final int pageSize;
    private final Deque<Long> buffer = new ArrayDeque<>();
    private long fetchedUpTo;
    private long lastReturnedId;
    private boolean lastPage;

    public PreferenceTargetReader(PreferenceTargets targets, LocalDateTime baseTime, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize는 1 이상이어야 합니다");
        }
        this.targets = requireNonNull(targets, "targets는 필수입니다");
        this.baseTime = requireNonNull(baseTime, "baseTime은 필수입니다");
        this.pageSize = pageSize;
    }

    // 덜 찬 페이지를 받으면 더 읽지 않는다
    @Override
    public Long read() {
        if (buffer.isEmpty() && !lastPage) {
            List<Long> ids = targets.findIds(baseTime, fetchedUpTo, pageSize);
            lastPage = ids.size() < pageSize;
            if (!ids.isEmpty()) {
                buffer.addAll(ids);
                fetchedUpTo = ids.getLast();
            }
        }
        Long id = buffer.poll();
        if (id != null) {
            lastReturnedId = id;
        }
        return id;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        lastReturnedId = executionContext.getLong(LAST_TARGET_ID, 0L);
        fetchedUpTo = lastReturnedId;
    }

    // 미리 읽어 두고 아직 돌려주지 않은 ID를 저장하면, 다시 실행할 때 그 대상들을 건너뛴다
    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putLong(LAST_TARGET_ID, lastReturnedId);
    }
}
