package com.bbcc.kidly.boundedcontext.recommend.app.preference;

/**
 * 선호 Job을 실행하라고 했을 때의 결과다.
 */
public enum PreferenceJobResult {
    COMPLETED,
    ALREADY_COMPLETED,
    IN_PROGRESS,
    LOCKED,
    FAILED
}
