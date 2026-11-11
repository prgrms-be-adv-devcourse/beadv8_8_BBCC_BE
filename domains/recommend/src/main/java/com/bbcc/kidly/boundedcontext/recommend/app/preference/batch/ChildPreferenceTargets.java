package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceWeights;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자녀 선호 대상이다. 자녀 활동과 구매확정으로 계산한다.
 */
public class ChildPreferenceTargets implements PreferenceTargets {

    private final PreferenceSourceReader sourceReader;
    private final PreferenceSnapshotStore snapshotStore;

    public ChildPreferenceTargets(PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore) {
        this.sourceReader = requireNonNull(sourceReader, "sourceReader는 필수입니다");
        this.snapshotStore = requireNonNull(snapshotStore, "snapshotStore는 필수입니다");
    }

    // 기간보다 하루 더 본다. 마지막 활동이 막 기간을 벗어난 자녀도 한 번 더 계산해 줄을 비운다
    @Override
    public List<Long> findIds(LocalDateTime baseTime, long afterId, int limit) {
        return sourceReader.findChildTargets(baseTime.minusDays(PreferenceWeights.ACTIVITY_WINDOW_DAYS + 1),
            baseTime.minusDays(PreferenceWeights.PURCHASE_WINDOW_DAYS + 1), afterId, limit);
    }

    // 기간 끝을 배치 기준 시각으로 두어, 그때까지 뺀 담기만 뺀 것으로 읽는다
    @Override
    public List<PreferenceSignal> findSignals(Long targetId, LocalDateTime baseTime) {
        return PreferenceSignals.of(
            sourceReader.findChildActivities(
                targetId, baseTime.minusDays(PreferenceWeights.ACTIVITY_WINDOW_DAYS), baseTime),
            sourceReader.findChildPurchases(
                targetId, baseTime.minusDays(PreferenceWeights.PURCHASE_WINDOW_DAYS), baseTime));
    }

    @Override
    public void replace(Long targetId, PreferenceSnapshot snapshot) {
        snapshotStore.replaceChild(targetId, snapshot);
    }
}
