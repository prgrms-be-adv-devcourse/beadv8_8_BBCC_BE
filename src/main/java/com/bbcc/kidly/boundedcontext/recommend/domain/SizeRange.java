package com.bbcc.kidly.boundedcontext.recommend.domain;

public record SizeRange(
        String size,
        int minHeightCm,
        int maxHeightCm
) {
    public static SizeRange of(
            String size,
            int minHeightCm,
            int maxHeightCm
    ) {
        return new SizeRange(
                size,
                minHeightCm,
                maxHeightCm
        );
    }

    public double distanceFrom(double heightCm) {
        if (heightCm < minHeightCm) {
            return minHeightCm - heightCm;
        }

        if (heightCm > maxHeightCm) {
            return heightCm - maxHeightCm;
        }

        return 0;
    }

    public double center() {
        return (minHeightCm + maxHeightCm) / 2.0;
    }
}
