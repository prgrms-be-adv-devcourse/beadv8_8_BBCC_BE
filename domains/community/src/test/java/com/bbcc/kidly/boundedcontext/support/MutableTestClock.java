package com.bbcc.kidly.boundedcontext.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트가 시각을 정하는 시계(시간대는 한국 시간). 지표처럼 "지금"을 시계로 받는 코드의 시각을 고정 · 이동한다.
 */
public final class MutableTestClock extends Clock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private volatile Instant now;

    public MutableTestClock(Instant now) {
        this.now = now;
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
