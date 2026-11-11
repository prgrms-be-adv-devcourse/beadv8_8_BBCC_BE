package com.bbcc.kidly.boundedcontext.recommend.domain.size;

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

    /**
     * 이 상품에 그 이름의 사이즈가 있는지 돌려준다. 권장 키가 있는 사이즈 줄만 본다.
     */
    public boolean hasSize(String size) {
        for (SizeRange range : sizeRanges) {
            if (range.size().equals(size)) {
                return true;
            }
        }
        return false;
    }
}
