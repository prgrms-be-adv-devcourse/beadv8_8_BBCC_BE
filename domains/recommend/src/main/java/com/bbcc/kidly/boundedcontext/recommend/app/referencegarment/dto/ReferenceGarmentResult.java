package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 자녀의 기준 옷 한 벌이다. 구분은 enum 이름(TOP · BOTTOM)이고, 실측은 항목 코드별 cm다.
 */
public record ReferenceGarmentResult(String part, Long categoryId, Map<String, BigDecimal> measurements) {

    public static ReferenceGarmentResult from(ReferenceGarment garment) {
        return new ReferenceGarmentResult(
            garment.getPart().name(), garment.getCategoryId(), garment.getMeasurements());
    }
}
