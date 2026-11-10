package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

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
        return new ReferenceGarmentSpec(part, categoryId, measurements);
    }
}
