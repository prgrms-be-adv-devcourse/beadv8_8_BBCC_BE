package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 관리자가 항목을 만들 때 보내는 요청이다.
 *
 * @param inputType 입력 방식의 이름(예: PALETTE)
 * @param values 항목과 함께 만들 목록 값이다. 넣은 순서대로 저장한다
 */
public record AttributeCreateRequest(String name, String code, String inputType, List<ValueInput> values) {

    /**
     * @param colorCode 팔레트 값일 때만 보낸다
     */
    public record ValueInput(String name, String colorCode) {
    }
}
