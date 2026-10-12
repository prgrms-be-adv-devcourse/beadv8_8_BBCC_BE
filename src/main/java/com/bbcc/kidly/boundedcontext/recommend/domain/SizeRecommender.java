package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SizeRecommender {

    private static final int OUT_OF_RANGE_LIMIT_CM = 2;
    private static final int EDGE_ZONE_CM = 2;
    private static final Comparator<SizeRange> BY_SIZE =
            Comparator.comparingInt(SizeRange::minHeightCm)
                    .thenComparingInt(SizeRange::maxHeightCm);

    private final GrowthChart growthChart;

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

        BodyType bodyType = bodyTypeFor(childSizeInput, today);
        SizeRange recommendedRange = chooseOne(candidates, childSizeInput, bodyType);

        return SizeRecommendation.recommended(
                recommendedRange.size(),
                fitOf(recommendedRange, childHeight, bodyType),
                List.of()
        );
    }

    private BodyType bodyTypeFor(ChildSizeInput childSizeInput, LocalDate today) {
        Optional<BodyType> judged = judgeByWeight(childSizeInput, today);
        if (judged.isEmpty()) {
            return childSizeInput.bodyType();
        }
        return judged.get();
    }

    private Optional<BodyType> judgeByWeight(ChildSizeInput childSizeInput, LocalDate today) {
        long months = Period.between(childSizeInput.birthDate(), today).toTotalMonths();

        return AgeGroup.fromMonths(months)
                .flatMap(ageGroup -> growthChart.find(
                        ageGroup,
                        childSizeInput.gender(),
                        ageGroup.roundHeight(childSizeInput.heightCm())))
                .map(percentiles -> percentiles.bodyTypeOf(childSizeInput.weightKg()));
    }

    private SizeRange chooseOne(List<SizeRange> candidates, ChildSizeInput childSizeInput, BodyType bodyType) {
        List<SizeRange> sorted = candidates.stream().sorted(BY_SIZE).toList();
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }

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
