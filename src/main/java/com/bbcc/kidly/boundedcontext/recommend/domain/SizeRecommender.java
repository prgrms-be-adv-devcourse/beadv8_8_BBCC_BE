package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
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
        if (productSizes.isSizeFree()) {
            return SizeRecommendation.sizeFree();
        }
        double childHeight = childSizeInput.heightCm();

        List<SizeRange> candidates = findCandidates(productSizes.sizeRanges(), childHeight);
        if (candidates.isEmpty()) {
            return SizeRecommendation.noMatchingSize();
        }

        SizeRange recommendedRange = chooseOne(candidates, childSizeInput);

        return SizeRecommendation.recommended(
                recommendedRange.size(),
                fitOf(recommendedRange, childHeight, childSizeInput.bodyType()),
                List.of()
        );
    }

    private SizeRange chooseOne(List<SizeRange> candidates, ChildSizeInput childSizeInput) {
        List<SizeRange> sorted = candidates.stream().sorted(BY_SIZE).toList();
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }

        BodyType bodyType = childSizeInput.bodyType();
        if (bodyType == BodyType.CHUNKY) {
            return sorted.getLast();
        }
        if (bodyType == BodyType.SLIM) {
            return sorted.getFirst();
        }

        if (childSizeInput.preferredFit() == PreferredFit.SLIM) {
            return sorted.getFirst();
        }

        if (childSizeInput.preferredFit() == PreferredFit.REGULAR) {
            return nearerToCenter(sorted.getFirst(), sorted.getLast(), childSizeInput.heightCm());
        }

        return sorted.getLast();
    }

    private SizeRange nearerToCenter(SizeRange smaller, SizeRange bigger, double childHeight) {
        double smallerDistance = Math.abs(childHeight - smaller.center());
        double biggerDistance = Math.abs(childHeight - bigger.center());

        if (smallerDistance < biggerDistance) {
            return smaller;
        }
        return bigger;
    }

    private Fit fitOf(SizeRange sizeRange, double childHeight, BodyType bodyType) {
        if (childHeight < sizeRange.minHeightCm()) {
            return Fit.ROOMY;
        }

        if (childHeight > sizeRange.maxHeightCm()) {
            return Fit.TIGHT;
        }

        return adjustByBodyType(fitInRange(sizeRange, childHeight), bodyType);
    }

    private Fit adjustByBodyType(Fit fit, BodyType bodyType) {
        if (bodyType == BodyType.CHUNKY) {
            return fit.tighter();
        }
        if (bodyType == BodyType.SLIM) {
            return fit.looser();
        }
        return fit;
    }

    private Fit fitInRange(SizeRange sizeRange, double childHeight) {
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
