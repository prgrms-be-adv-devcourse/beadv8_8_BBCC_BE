package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class ReferenceGarmentSpec {

    private final GarmentPart part;
    private final Long categoryId;
    private final Map<String, BigDecimal> measurements;

    private ReferenceGarmentSpec(GarmentPart part, Long categoryId, Map<String, BigDecimal> measurements) {
        this.part = part;
        this.categoryId = categoryId;
        this.measurements = measurements;
    }

    public static ReferenceGarmentSpec of(GarmentPart part, Long categoryId, List<String> categoryPathNames,
        Map<String, BigDecimal> measurements) {
        if (categoryPathNames == null || categoryPathNames.isEmpty()) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!part.includesCategoryIn(categoryPathNames)) {
            throw new BusinessException(ReferenceGarmentErrorCode.CATEGORY_PART_MISMATCH);
        }
        return new ReferenceGarmentSpec(part, categoryId, measurements);
    }
}
