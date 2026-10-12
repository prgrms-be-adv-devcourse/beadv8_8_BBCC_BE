package com.bbcc.kidly.boundedcontext.recommend.domain;

import java.util.Arrays;
import java.util.Optional;

public enum AgeGroup {

    MONTHS_0_23(0, 23, 0.5),
    MONTHS_24_35(24, 35, 0.5),
    YEARS_3_18(36, 227, 1.0);

    private final int minMonths;
    private final int maxMonths;
    private final double heightStepCm;

    AgeGroup(int minMonths, int maxMonths, double heightStepCm) {
        this.minMonths = minMonths;
        this.maxMonths = maxMonths;
        this.heightStepCm = heightStepCm;
    }

    public static Optional<AgeGroup> fromMonths(long months) {
        return Arrays.stream(values())
                .filter(group -> group.minMonths <= months && months <= group.maxMonths)
                .findFirst();
    }

    public double roundHeight(double heightCm) {
        return Math.round(heightCm / heightStepCm) * heightStepCm;
    }
}
