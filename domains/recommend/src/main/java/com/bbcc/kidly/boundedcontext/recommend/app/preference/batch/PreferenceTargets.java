package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSignal;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 선호를 계산할 대상(자녀 또는 회원)마다 다른 읽기 · 저장을 모은다. 자녀 Step과 회원 Step은 이것만 다르다.
 */
public interface PreferenceTargets {

    /**
     * 배치 기준 시각으로 정한 기간 안에 활동이 있는 대상 ID를 afterId 다음부터 ID 오름차순으로 limit개까지 읽는다.
     */
    List<Long> findIds(LocalDateTime baseTime, long afterId, int limit);

    /**
     * 대상 하나의 기간 안 활동 · 구매확정을 선호 신호로 읽는다.
     */
    List<PreferenceSignal> findSignals(Long targetId, LocalDateTime baseTime);

    /**
     * 대상 하나의 저장된 선호를 스냅숏으로 바꾼다.
     */
    void replace(Long targetId, PreferenceSnapshot snapshot);
}
