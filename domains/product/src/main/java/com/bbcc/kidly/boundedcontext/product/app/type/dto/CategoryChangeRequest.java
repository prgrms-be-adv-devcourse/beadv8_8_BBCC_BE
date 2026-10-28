package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 관리자가 말단 카테고리의 설정을 바꿀 때 보내는 요청이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param productTypeId 바꿀 상품 유형 ID다
 * @param measurementItemIds 바꿀 필수 실측 항목 ID다. 넣은 순서가 실측 항목 순서가 된다. 없으면 빈 목록을 보낸다
 */
public record CategoryChangeRequest(
    @NotNull(message = "상품 유형 ID를 보내 주세요.")
    Long productTypeId,
    @NotNull(message = "실측 항목 ID를 보내 주세요. 없으면 빈 목록을 보내 주세요.")
    List<@NotNull(message = "실측 항목 ID를 넣어 주세요.") Long> measurementItemIds) {
}
