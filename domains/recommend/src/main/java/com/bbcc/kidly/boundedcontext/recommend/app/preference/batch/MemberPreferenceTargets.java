package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 선호 대상이다. 자녀를 고르지 않은 회원 활동으로 계산한다.
 */
public class MemberPreferenceTargets implements PreferenceTargets {

    public MemberPreferenceTargets(PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore) {
    }

    @Override
    public List<Long> findIds(LocalDateTime baseTime, long afterId, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PreferenceSignal> findSignals(Long targetId, LocalDateTime baseTime) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replace(Long targetId, PreferenceSnapshot snapshot) {
        throw new UnsupportedOperationException();
    }
}
