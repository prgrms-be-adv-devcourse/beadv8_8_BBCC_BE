package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 말단 카테고리의 상품 등록 화면이 받는 입력 항목이다.
 *
 * @param attributes 옵션 항목부터, 각 역할 안에서는 상품 유형에 연결한 순서대로 담는다
 * @param measurementItems 카테고리에 연결한 순서대로 담는다
 */
public record CategoryInputsResult(
    Long categoryId,
    String categoryName,
    Long productTypeId,
    String productTypeName,
    List<InputAttribute> attributes,
    List<Measurement> measurementItems) {

    /**
     * @param inputType 입력 방식의 이름(예: PALETTE)
     * @param role 항목의 역할 이름(OPTION 또는 INFO)
     * @param values 상품 유형에서 고를 수 있는 값을 ID 순서로 담는다. 직접 입력 항목이면 빈 목록이다
     */
    public record InputAttribute(
        Long attributeId,
        String code,
        String name,
        String inputType,
        String role,
        boolean required,
        boolean sizeRole,
        boolean priceAffecting,
        List<SelectableValue> values) {
    }

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record SelectableValue(Long valueId, String value, String colorCode) {
    }

    public record Measurement(String code, String name, String unit) {
    }
}
