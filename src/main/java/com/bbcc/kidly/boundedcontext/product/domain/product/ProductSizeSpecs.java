package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;

/**
 * 실측을 추가할 때 실측 여러 개나 요청 전체를 함께 보는 검사를 모은다. Product.addSizeSpecs만 쓴다.
 */
final class ProductSizeSpecs {

    private ProductSizeSpecs() {
    }

    // 권장 키는 최솟값과 최댓값을 함께 넣어야 하고, 최솟값이 최댓값보다 크면 안 된다 (SIZE-03)
    static void validateHeightRanges(List<SizeSpecInfo> specs) {
        for (SizeSpecInfo spec : specs) {
            Integer min = spec.minHeightCm();
            Integer max = spec.maxHeightCm();
            if ((min == null) != (max == null)) {
                throw new BusinessException(ProductErrorCode.INVALID_HEIGHT_RANGE);
            }
            if (min != null && min > max) {
                throw new BusinessException(ProductErrorCode.INVALID_HEIGHT_RANGE);
            }
        }
    }
}
