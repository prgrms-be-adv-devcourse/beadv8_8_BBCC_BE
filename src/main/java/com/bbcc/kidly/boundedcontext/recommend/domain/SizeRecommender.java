package com.bbcc.kidly.boundedcontext.recommend.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class SizeRecommender {

    private static final int OUT_OF_RANGE_LIMIT_CM = 2;
    private static final int EDGE_ZONE_CM = 2;
    private static final Comparator<SizeRange> BY_SIZE =
            Comparator.comparingInt(SizeRange::minHeightCm)
                    .thenComparingInt(SizeRange::maxHeightCm);

    public SizeRecommendation recommend(
            ChildSizeInput childSizeInput,
            ProductSizes productSizes,
            LocalDate today
    ) {
        double childHeight = childSizeInput.heightCm();

        List<SizeRange> candidates = findCandidates(productSizes.sizeRanges(), childHeight);
        if (candidates.isEmpty()) {
            return SizeRecommendation.noMatchingSize();
        }

        SizeRange recommendedRange = chooseOne(candidates, childSizeInput);

        return SizeRecommendation.recommended(
                recommendedRange.size(),
                fitOf(recommendedRange, childHeight),
                List.of()
        );
    }

    private SizeRange chooseOne(List<SizeRange> candidates, ChildSizeInput childSizeInput) {
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        if (childSizeInput.preferredFit() == PreferredFit.LOOSE) {
            return candidates.stream().max(BY_SIZE).orElseThrow();
        }

        return candidates.stream().min(BY_SIZE).orElseThrow();
    }

    private Fit fitOf(SizeRange sizeRange, double childHeight) {
        if (childHeight < sizeRange.minHeightCm()) {
            return Fit.ROOMY;
        }

        if (childHeight > sizeRange.maxHeightCm()) {
            return Fit.TIGHT;
        }

        boolean inLowerEdge = childHeight <= sizeRange.minHeightCm() + EDGE_ZONE_CM;
        boolean inUpperEdge = sizeRange.maxHeightCm() - EDGE_ZONE_CM <= childHeight;

        if (inLowerEdge && inUpperEdge) {
            return Fit.PROPER;
        }

        if (inUpperEdge) {
            return Fit.TIGHT;
        }

        if (inLowerEdge) {
            return Fit.ROOMY;
        }

        return Fit.PROPER;
    }

    private List<SizeRange> findCandidates(List<SizeRange> sizeRanges, double childHeight) {
        List<SizeRange> inRange = sizeRanges.stream()
                .filter(range -> range.distanceFrom(childHeight) == 0)
                .toList();
        if (!inRange.isEmpty()) {
            return inRange;
        }

        return sizeRanges.stream()
                .filter(range -> isNearby(range, childHeight))
                .toList();
    }

    private boolean isNearby(SizeRange range, double childHeight) {
        double distance = range.distanceFrom(childHeight);
        return distance > 0 && distance <= OUT_OF_RANGE_LIMIT_CM;
    }
}
