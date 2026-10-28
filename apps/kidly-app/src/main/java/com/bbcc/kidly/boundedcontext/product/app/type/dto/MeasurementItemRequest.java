package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 관리자가 실측 항목을 만들 때 보내는 요청이다.
 *
 * @param code 상품 실측 값의 키로 쓰이는 코드다
 * @param unit 실측 단위(예: cm)
 */
public record MeasurementItemRequest(
    @NotBlank(message = "실측 항목 이름을 입력해 주세요.")
    @Size(max = 255, message = "실측 항목 이름은 255자 이하로 입력해 주세요.")
    String name,
    // 항목 코드와 같은 형식으로 맞춰 대문자와 _만 받는다
    @NotBlank(message = "실측 항목 코드를 입력해 주세요.")
    @Size(max = 255, message = "실측 항목 코드는 255자 이하로 입력해 주세요.")
    @Pattern(regexp = "[A-Z_]+", message = "실측 항목 코드는 대문자와 _만 쓸 수 있습니다.")
    String code,
    @NotBlank(message = "실측 단위를 입력해 주세요.")
    @Size(max = 255, message = "실측 단위는 255자 이하로 입력해 주세요.")
    String unit) {
}
