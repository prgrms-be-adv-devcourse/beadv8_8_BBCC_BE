package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자녀 선호 대상이다. 자녀 활동과 구매확정으로 계산한다.
 */
public class ChildPreferenceTargets implements PreferenceTargets {

    public ChildPreferenceTargets(PreferenceSourceReader sourceReader, PreferenceSnapshotStore snapshotStore) {
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
