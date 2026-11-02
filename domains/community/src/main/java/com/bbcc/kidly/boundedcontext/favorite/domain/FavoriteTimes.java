package com.bbcc.kidly.boundedcontext.favorite.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 저장했다가 다시 정렬 · 커서에 쓰는 시각을 DB 정밀도(마이크로초)로 맞춘다. 실행 환경에 따라 LocalDateTime이 나노초까지 나오는데
 * DB는 마이크로초로 저장하므로 자르지 않으면 같은 시각이 다르게 보일 수 있다.
 */
public final class FavoriteTimes {

    private FavoriteTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
