package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param inputType 입력 방식의 이름(예: PALETTE). 정해지지 않은 값도 같은 400으로 거절하려고 글자로 받는다
 * @param values 항목과 함께 만들 목록 값이다. 넣은 순서대로 저장한다
 */
public record AttributeCreateRequest(
    @NotBlank(message = "항목 이름을 입력해 주세요.")
    @Size(max = 255, message = "항목 이름은 255자 이하로 입력해 주세요.")
    String name,
    @NotBlank(message = "항목 코드를 입력해 주세요.")
    @Size(max = 255, message = "항목 코드는 255자 이하로 입력해 주세요.")
    @Pattern(regexp = "[A-Z_]+", message = "항목 코드는 대문자와 _만 쓸 수 있습니다.")
    String code,
    @NotNull(message = INPUT_TYPE_MESSAGE)
    @Pattern(regexp = "PALETTE|LIST|MULTI_LIST|TEXT", message = INPUT_TYPE_MESSAGE)
    String inputType,
    @NotNull(message = "목록 값을 보내 주세요. 없으면 빈 목록을 보내 주세요.")
    List<@Valid AttributeValueRequest> values) {

    private static final String INPUT_TYPE_MESSAGE =
        "입력 방식은 PALETTE, LIST, MULTI_LIST, TEXT 중 하나로 보내 주세요.";
}
