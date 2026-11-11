package com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentResult;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 자녀의 기준 옷 한 벌이다. ID는 다른 응답처럼 문자열로 보낸다.
 *
 * @param part TOP · BOTTOM · OUTER · DRESS · SKIRT 중 하나
 * @param categoryId 세부 종류인 상품 말단 카테고리의 ID
 * @param measurements 실측 항목 코드별 cm. 소수 첫째 자리까지 보내고, 입력하지 않은 선택 항목은 없다
 */
public record ReferenceGarmentResponse(String part, String categoryId, Map<String, BigDecimal> measurements) {

    public static ReferenceGarmentResponse from(ReferenceGarmentResult result) {
        return new ReferenceGarmentResponse(
            result.part(), String.valueOf(result.categoryId()), result.measurements());
    }
}
