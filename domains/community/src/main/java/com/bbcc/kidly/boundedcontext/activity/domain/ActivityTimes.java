package com.bbcc.kidly.boundedcontext.activity.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 활동 시각을 DB 정밀도(마이크로초)로 맞춘다.
 */
public final class ActivityTimes {

    private ActivityTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
