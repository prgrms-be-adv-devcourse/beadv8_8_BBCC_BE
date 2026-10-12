package com.bbcc.kidly.boundedcontext.recommend.domain;

import java.util.List;

public record ProductSizes(
        boolean isSizeFree,
        List<SizeRange> sizeRanges
) {
    public static ProductSizes of(
            boolean isSizeFree,
            List<SizeRange> sizeRanges
    ) {
        return new ProductSizes(
                isSizeFree,
                sizeRanges
        );
    }
}
