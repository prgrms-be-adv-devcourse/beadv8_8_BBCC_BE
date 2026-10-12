package com.bbcc.kidly.boundedcontext.recommend.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SizeRecommender {

    private static final double OUT_OF_RANGE_LIMIT_CM = 2;

    public SizeRecommendation recommend(
            ChildSizeInput childSizeInput,
            ProductSizes productSizes,
            LocalDate today
    ) {
        double childHeight = childSizeInput.heightCm();

        Optional<SizeRange> candidate = findCandidate(productSizes.sizeRanges(), childHeight);
        if (candidate.isEmpty()) {
            return SizeRecommendation.noMatchingSize();
        }

        return SizeRecommendation.recommended(candidate.get().size(), Fit.PROPER, List.of());
    }

    private Optional<SizeRange> findCandidate(List<SizeRange> sizeRanges, double childHeight) {
        Optional<SizeRange> inRange = sizeRanges.stream()
                .filter(range -> range.distanceFrom(childHeight) == 0)
                .findFirst();
        if (inRange.isPresent()) {
            return inRange;
        }

        return sizeRanges.stream()
                .filter(range -> isNearby(range, childHeight))
                .findFirst();
    }

    private boolean isNearby(SizeRange range, double childHeight) {
        double distance = range.distanceFrom(childHeight);
        return distance > 0 && distance <= OUT_OF_RANGE_LIMIT_CM;
    }
}
