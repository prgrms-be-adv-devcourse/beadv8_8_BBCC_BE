package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.ProductTypeResult;
import java.util.ArrayList;
import java.util.List;

public record ProductTypeResponse(String productTypeId, String name, List<Link> attributes) {

    /**
     * @param inputType 입력 방식의 이름(예: PALETTE)
     * @param role 항목의 역할 이름(OPTION 또는 INFO)
     */
    public record Link(
        String attributeId,
        String code,
        String name,
        String inputType,
        String role,
        boolean required,
        boolean allValuesAllowed,
        boolean sizeRole,
        boolean priceAffecting,
        List<AttributeResponse.Value> allowedValues) {

        public static Link from(ProductTypeResult.Link result) {
            List<AttributeResponse.Value> allowedValues = new ArrayList<>();
            for (AttributeResult.Value value : result.allowedValues()) {
                allowedValues.add(AttributeResponse.Value.from(value));
            }
            return new Link(
                String.valueOf(result.attributeId()),
                result.code(),
                result.name(),
                result.inputType(),
                result.role(),
                result.required(),
                result.allValuesAllowed(),
                result.sizeRole(),
                result.priceAffecting(),
                allowedValues);
        }
    }

    public static ProductTypeResponse from(ProductTypeResult result) {
        List<Link> attributes = new ArrayList<>();
        for (ProductTypeResult.Link link : result.attributes()) {
            attributes.add(Link.from(link));
        }
        return new ProductTypeResponse(String.valueOf(result.productTypeId()), result.name(), attributes);
    }
}
