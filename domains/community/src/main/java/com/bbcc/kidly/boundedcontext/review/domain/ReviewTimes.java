package com.bbcc.kidly.boundedcontext.review.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 저장했다가 다시 비교 · 정렬 · 커서에 쓰는 시각을 DB 정밀도(마이크로초)로 맞춘다. 실행 환경에 따라 LocalDateTime이 나노초까지
 * 나오는데 DB는 마이크로초로 저장하므로(반올림), 자르지 않으면 같은 이벤트의 시각이 다시 읽은 저장 값보다 "늦은" 값으로 보일 수 있다.
 * 마이크로초 아래만 다른 두 시각은 같은 시각으로 본다.
 */
public final class ReviewTimes {

    private ReviewTimes() {
    }

    public static LocalDateTime toStoredPrecision(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    public static LocalDateTime now() {
        return toStoredPrecision(LocalDateTime.now());
    }
}
