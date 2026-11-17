package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

public record ProductTypeResult(Long productTypeId, String name, List<Link> attributes) {

    public record Link(
        Long attributeId,
        String code,
        String name,
        String inputType,
        String role,
        boolean required,
        boolean allValuesAllowed,
        boolean sizeRole,
        boolean priceAffecting,
        List<AttributeResult.Value> allowedValues) {
    }
}
