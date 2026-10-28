package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 항목에 더할 목록 값 하나다.
 *
 * @param colorCode 팔레트 값일 때만 보낸다. 없으면 null이다
 */
public record AttributeValueRequest(
    @NotBlank(message = "목록 값 이름을 입력해 주세요.")
    @Size(max = 255, message = "목록 값 이름은 255자 이하로 입력해 주세요.")
    String name,
    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "색 코드는 #rrggbb 형식으로 입력해 주세요.")
    String colorCode) {
}
