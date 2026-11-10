package com.bbcc.kidly.boundedcontext.recommend.app.preference.batch;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;

/**
 * 대상 하나의 선호 계산 결과다. 대상 ID는 자녀 Step이면 자녀 ID, 회원 Step이면 회원 ID다.
 */
public record PreferenceResult(Long targetId, PreferenceSnapshot snapshot) {
}
