package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import java.math.BigDecimal;
import java.util.Map;

public record ReferenceGarmentResult(String part, Long categoryId, Map<String, BigDecimal> measurements) {

    public static ReferenceGarmentResult from(ReferenceGarment garment) {
        return new ReferenceGarmentResult(
            garment.getPart().name(), garment.getCategoryId(), garment.getMeasurements());
    }
}
