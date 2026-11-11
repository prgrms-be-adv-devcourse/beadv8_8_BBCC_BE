package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 기준 옷 한 벌을 저장하는 요청이다. 구분은 주소의 {part}로 받는다.
 * JSON의 문자열 ID("7")도 Long으로 받는다. 실측 항목과 값의 검사는 기준 옷 도메인이 한다.
 *
 * @param categoryId 세부 종류인 상품 말단 카테고리의 ID
 * @param measurements 실측 항목 코드별 cm
 */
public record ReferenceGarmentRequest(
    @NotNull(message = "세부 종류 ID가 필요합니다.") @Positive(message = "세부 종류 ID는 1 이상이어야 합니다.") Long categoryId,
    @NotNull(message = "실측을 입력해 주세요.") Map<String, BigDecimal> measurements) {
}
