package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import static java.util.Objects.requireNonNull;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * chunk의 대상마다 저장된 선호를 새 계산으로 바꾼다. 저장은 chunk 트랜잭션에 합류하므로 chunk 하나가 함께 커밋되거나 롤백된다.
 */
public class PreferenceTargetWriter implements ItemWriter<PreferenceResult> {

    private final PreferenceTargets targets;

    public PreferenceTargetWriter(PreferenceTargets targets) {
        this.targets = requireNonNull(targets, "targets는 필수입니다");
    }

    @Override
    public void write(Chunk<? extends PreferenceResult> results) {
        for (PreferenceResult result : results) {
            targets.replace(result.targetId(), result.snapshot());
        }
    }
}
