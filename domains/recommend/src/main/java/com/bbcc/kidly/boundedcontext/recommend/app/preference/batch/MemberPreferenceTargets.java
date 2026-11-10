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
 * 회원 선호 대상이다. 자녀를 고르지 않은 회원 활동으로 계산한다.
 */
public class MemberPreferenceTargets implements PreferenceTargets {

    private final PreferenceSourceReader sourceReader;
    private final PreferenceSnapshotStore snapshotStore;

    public MemberPreferenceTargets(PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore) {
        this.sourceReader = requireNonNull(sourceReader, "sourceReader는 필수입니다");
        this.snapshotStore = requireNonNull(snapshotStore, "snapshotStore는 필수입니다");
    }

    // 기간보다 하루 더 본다. 마지막 활동이 막 기간을 벗어난 회원도 한 번 더 계산해 줄을 비운다
    @Override
    public List<Long> findIds(LocalDateTime baseTime, long afterId, int limit) {
        return sourceReader.findMemberTargets(
            baseTime.minusDays(PreferenceWeights.ACTIVITY_WINDOW_DAYS + 1), afterId, limit);
    }

    // 구매 기록은 늘 자녀가 있어 회원 몫에는 구매확정이 없다
    @Override
    public List<PreferenceSignal> findSignals(Long targetId, LocalDateTime baseTime) {
        return PreferenceSignals.of(
            sourceReader.findMemberActivities(
                targetId, baseTime.minusDays(PreferenceWeights.ACTIVITY_WINDOW_DAYS), baseTime),
            List.of());
    }

    @Override
    public void replace(Long targetId, PreferenceSnapshot snapshot) {
        snapshotStore.replaceMember(targetId, snapshot);
    }
}
