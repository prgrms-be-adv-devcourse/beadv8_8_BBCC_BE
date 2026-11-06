package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.util.Arrays;
import java.util.Optional;

enum AgeGroup {

    MONTHS_0_23(0, 23),
    MONTHS_24_227(24, 227);

    private final int minMonths;
    private final int maxMonths;

    AgeGroup(int minMonths, int maxMonths) {
        this.minMonths = minMonths;
        this.maxMonths = maxMonths;
    }

    static Optional<AgeGroup> fromMonths(long months) {
        return Arrays.stream(values())
                .filter(group -> group.minMonths <= months && months <= group.maxMonths)
                .findFirst();
    }
}
