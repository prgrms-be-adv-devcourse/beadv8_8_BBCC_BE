package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

public record CategoryInputsResult(
    Long categoryId,
    String categoryName,
    Long productTypeId,
    String productTypeName,
    List<InputAttribute> attributes,
    List<Measurement> measurementItems) {

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

    public record SelectableValue(Long valueId, String value, String colorCode) {
    }

    public record Measurement(String code, String name, String unit) {
    }
}
