package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관리자가 상품 유형을 만들 때 보내는 요청이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param attributes 유형에 연결할 항목이다. 넣은 순서가 연결 순서가 된다
 */
public record ProductTypeCreateRequest(
    @NotBlank(message = "유형 이름을 입력해 주세요.")
    @Size(max = 255, message = "유형 이름은 255자 이하로 입력해 주세요.")
    String name,
    @NotNull(message = "연결할 항목을 보내 주세요.")
    List<@Valid @NotNull(message = "연결할 항목 설정을 넣어 주세요.") Link> attributes) {

    /**
     * 유형에 항목 하나를 연결하는 설정이다.
     *
     * @param role 항목의 역할 이름(OPTION 또는 INFO). 정해지지 않은 값도 같은 400으로 거절하려고 글자로 받는다
     * @param allowedValueIds 모든 값 허용이 아닐 때 고를 수 있는 목록 값 ID다. 없으면 빈 목록을 보낸다
     */
    public record Link(
        @NotNull(message = "항목 ID를 입력해 주세요.")
        Long attributeId,
        @NotNull(message = ROLE_MESSAGE)
        @Pattern(regexp = "OPTION|INFO", message = ROLE_MESSAGE)
        String role,
        boolean required,
        boolean allValuesAllowed,
        boolean sizeRole,
        boolean priceAffecting,
        @NotNull(message = "허용할 값 ID를 보내 주세요. 없으면 빈 목록을 보내 주세요.")
        List<@NotNull(message = "허용할 값 ID를 넣어 주세요.") Long> allowedValueIds) {

        private static final String ROLE_MESSAGE = "항목 역할은 OPTION, INFO 중 하나로 보내 주세요.";
    }
}
