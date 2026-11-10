package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 기준 옷 한 벌을 저장하는 요청이다. 구분은 주소의 {part}로 받는다.
 * JSON의 문자열 ID("7")도 Long으로 받는다.
 *
 * @param categoryId 세부 종류인 상품 말단 카테고리의 ID
 * @param measurements 실측 항목 코드별 cm
 */
public record ReferenceGarmentRequest(Long categoryId, Map<String, BigDecimal> measurements) {
}
