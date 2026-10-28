package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeCreateRequest.Link;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 관리자가 상품 유형의 연결을 바꿀 때 보내는 요청이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param attributes 유형에 연결할 항목이다. 이 목록으로 기존 연결을 통째로 바꾸고, 넣은 순서가 연결 순서가 된다
 */
public record ProductTypeChangeRequest(
    @NotNull(message = "연결할 항목을 보내 주세요.")
    List<@Valid @NotNull(message = "연결할 항목 설정을 넣어 주세요.") Link> attributes) {
}
