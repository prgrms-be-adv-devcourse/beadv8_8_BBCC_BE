package com.bbcc.kidly.boundedcontext.recommend.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SizeRecommender {

    public SizeRecommendation recommend(
            ChildSizeInput childSizeInput,
            ProductSizes productSizes,
            LocalDate today
    ) {
        double childHeight = childSizeInput.heightCm();

        Optional<SizeRange> matchedRange = productSizes.sizeRanges()
                .stream()
                .filter(range ->
                        range.minHeightCm() <= childHeight
                                && childHeight <= range.maxHeightCm())
                .findFirst();

        if (matchedRange.isEmpty()) {
            return SizeRecommendation.noMatchingSize();
        }

        return SizeRecommendation.recommended(
                matchedRange.get().size(),
                Fit.PROPER,
                List.of()
        );
    }
}
