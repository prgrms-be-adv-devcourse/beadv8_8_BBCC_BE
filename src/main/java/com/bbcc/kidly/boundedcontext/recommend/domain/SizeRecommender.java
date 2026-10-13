package com.bbcc.kidly.boundedcontext.recommend.domain;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        List<SizeRange> sizeRanges = distinctBySize(productSizes.sizeRanges());
        List<SizeRange> candidates = sortBySize(findCandidates(sizeRanges, childHeight));
        if (candidates.isEmpty()) {
            return SizeRecommendation.noMatchingSize();
        }

        BodyTypeDecision bodyTypeDecision = decideBodyType(childSizeInput, today);
        BodyType bodyType = bodyTypeDecision.bodyType();
        SizeRange recommendedRange = chooseOne(candidates, childSizeInput, bodyType);

        return SizeRecommendation.recommended(
                recommendedRange.size(),
                fitOf(recommendedRange, childHeight, bodyType),
                noticesFor(bodyTypeDecision, candidates, recommendedRange, childSizeInput)
        );
    }

    private BodyTypeDecision decideBodyType(ChildSizeInput childSizeInput, LocalDate today) {
        BodyType input = childSizeInput.bodyType();
        Optional<BodyType> judged = judgeByWeight(childSizeInput, today);
        if (judged.isEmpty()) {
            return BodyTypeDecision.unchanged(input);
        }
        if (isTwoStepsApart(input, judged.get())) {
            return BodyTypeDecision.heightOnly();
        }
        if (input != null && input != judged.get()) {
            return BodyTypeDecision.adjusted(judged.get());
        }
        return BodyTypeDecision.unchanged(judged.get());
    }

    private List<SizeNotice> noticesFor(
            BodyTypeDecision bodyTypeDecision,
            List<SizeRange> candidates,
            SizeRange recommendedRange,
            ChildSizeInput childSizeInput
    ) {
        if (!isBodyTypeUsed(candidates, recommendedRange, childSizeInput.heightCm())) {
            return List.of();
        }
        if (bodyTypeDecision.basis() == BodyTypeDecision.Basis.HEIGHT_ONLY) {
            return List.of(SizeNotice.HEIGHT_ONLY);
        }

        List<SizeNotice> notices = new ArrayList<>();
        if (bodyTypeDecision.basis() == BodyTypeDecision.Basis.ADJUSTED) {
            notices.add(SizeNotice.BODY_TYPE_ADJUSTED);
        }
        if (isPreferredFitOverridden(candidates, recommendedRange, childSizeInput, bodyTypeDecision.bodyType())) {
            notices.add(SizeNotice.PREFERRED_FIT_NOT_APPLIED);
        }
        return List.copyOf(notices);
    }

    private boolean isPreferredFitOverridden(
            List<SizeRange> candidates,
            SizeRange recommendedRange,
            ChildSizeInput childSizeInput,
            BodyType bodyType
    ) {
        if (candidates.size() == 1 || childSizeInput.preferredFit() == null) {
            return false;
        }
        if (bodyType != BodyType.CHUNKY && bodyType != BodyType.SLIM) {
            return false;
        }
        return !chooseByPreferredFit(candidates, childSizeInput).equals(recommendedRange);
    }

    private boolean isBodyTypeUsed(List<SizeRange> candidates, SizeRange recommendedRange, double childHeight) {
        if (candidates.size() > 1) {
            return true;
        }
        return recommendedRange.distanceFrom(childHeight) == 0;
    }

    private boolean isTwoStepsApart(BodyType input, BodyType judged) {
        return input == BodyType.SLIM && judged == BodyType.CHUNKY
                || input == BodyType.CHUNKY && judged == BodyType.SLIM;
    }

    private Optional<BodyType> judgeByWeight(ChildSizeInput childSizeInput, LocalDate today) {
        long months = Period.between(childSizeInput.birthDate(), today).toTotalMonths();
        Optional<AgeGroup> ageGroup = AgeGroup.fromMonths(months);
        if (ageGroup.isEmpty()) {
            return Optional.empty();
        }

        double chartHeight = ageGroup.get().roundHeight(childSizeInput.heightCm());
        Optional<WeightPercentiles> percentiles = growthChart.find(
                ageGroup.get(), childSizeInput.gender(), chartHeight);
        if (percentiles.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(percentiles.get().bodyTypeOf(childSizeInput.weightKg()));
    }

    private List<SizeRange> distinctBySize(List<SizeRange> sizeRanges) {
        Map<String, SizeRange> bySize = new LinkedHashMap<>();
        for (SizeRange sizeRange : sizeRanges) {
            bySize.putIfAbsent(sizeRange.size(), sizeRange);
        }
        return List.copyOf(bySize.values());
    }

    private List<SizeRange> sortBySize(List<SizeRange> sizeRanges) {
        return sizeRanges.stream().sorted(BY_SIZE).toList();
    }

    private SizeRange chooseOne(List<SizeRange> sorted, ChildSizeInput childSizeInput, BodyType bodyType) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }

        if (bodyType == BodyType.CHUNKY) {
            return oneSizeBigger(sorted, childSizeInput.heightCm());
        }
        if (bodyType == BodyType.SLIM) {
            return oneSizeSmaller(sorted, childSizeInput.heightCm());
        }

        return chooseByPreferredFit(sorted, childSizeInput);
    }

    private SizeRange chooseByPreferredFit(List<SizeRange> sorted, ChildSizeInput childSizeInput) {
        PreferredFit preferredFit = childSizeInput.preferredFit();
        if (preferredFit == PreferredFit.SLIM) {
            return oneSizeSmaller(sorted, childSizeInput.heightCm());
        }
        if (preferredFit == PreferredFit.LOOSE) {
            return oneSizeBigger(sorted, childSizeInput.heightCm());
        }
        if (preferredFit == null && sorted.size() == 2) {
            return sorted.getLast();
        }

        return sorted.get(nearestToCenterIndex(sorted, childSizeInput.heightCm()));
    }

    private SizeRange oneSizeBigger(List<SizeRange> sorted, double childHeight) {
        int index = nearestToCenterIndex(sorted, childHeight) + 1;
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private SizeRange oneSizeSmaller(List<SizeRange> sorted, double childHeight) {
        int index = nearestToCenterIndex(sorted, childHeight) - 1;
        return sorted.get(Math.max(index, 0));
    }

    private int nearestToCenterIndex(List<SizeRange> sorted, double childHeight) {
        int nearestIndex = 0;
        for (int i = 1; i < sorted.size(); i++) {
            double distance = Math.abs(childHeight - sorted.get(i).center());
            double nearestDistance = Math.abs(childHeight - sorted.get(nearestIndex).center());
            if (distance <= nearestDistance) {
                nearestIndex = i;
            }
        }
        return nearestIndex;
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
