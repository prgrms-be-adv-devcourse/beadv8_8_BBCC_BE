package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 만든 항목과 그 목록 값이다.
 *
 * @param inputType 입력 방식의 이름(예: PALETTE)
 * @param values 목록 값을 ID 순서로 담는다
 */
public record AttributeResult(Long attributeId, String name, String code, String inputType, List<Value> values) {

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record Value(Long valueId, String name, String colorCode) {
    }
}
