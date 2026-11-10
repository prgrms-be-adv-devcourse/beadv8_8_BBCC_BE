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
 */
public class PreferenceTargetReader implements ItemStreamReader<Long> {

    private final PreferenceTargets targets;
    private final LocalDateTime baseTime;
    private final int pageSize;
    private final Deque<Long> buffer = new ArrayDeque<>();
    private long fetchedUpTo;
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
        return buffer.poll();
    }

    @Override
    public void open(ExecutionContext executionContext) {
    }

    @Override
    public void update(ExecutionContext executionContext) {
    }
}
