package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import java.time.LocalDateTime;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * 선호를 계산할 대상 ID를 오름차순으로 한 페이지씩 읽는다.
 */
public class PreferenceTargetReader implements ItemStreamReader<Long> {

    public PreferenceTargetReader(PreferenceTargets targets, LocalDateTime baseTime, int pageSize) {
    }

    @Override
    public Long read() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void open(ExecutionContext executionContext) {
    }

    @Override
    public void update(ExecutionContext executionContext) {
    }
}
