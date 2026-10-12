package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.member.domain.BodyType;

public record WeightPercentiles(
        double percentile15Kg,
        double percentile85Kg
) {

    public static WeightPercentiles of(double percentile15Kg, double percentile85Kg) {
        return new WeightPercentiles(percentile15Kg, percentile85Kg);
    }

    public BodyType bodyTypeOf(double weightKg) {
        if (weightKg <= percentile15Kg) {
            return BodyType.SLIM;
        }
        if (weightKg > percentile85Kg) {
            return BodyType.CHUNKY;
        }
        return BodyType.NORMAL;
    }
}
