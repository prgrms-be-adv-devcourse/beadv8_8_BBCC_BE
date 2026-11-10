package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.math.BigDecimal;
import java.util.Map;

public class ReferenceGarment {

    private Long childProfileId;
    private GarmentPart part;
    private Long categoryId;
    private Map<String, BigDecimal> measurements;

    protected ReferenceGarment() {
    }

    public static ReferenceGarment create(Long childProfileId, ReferenceGarmentSpec spec) {
        throw new UnsupportedOperationException();
    }

    public void replace(ReferenceGarmentSpec spec) {
        throw new UnsupportedOperationException();
    }

    public Long getChildProfileId() {
        return childProfileId;
    }

    public GarmentPart getPart() {
        return part;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Map<String, BigDecimal> getMeasurements() {
        return measurements;
    }
}
