package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @param attributes 옵션 항목부터, 각 역할 안에서는 상품 유형에 연결한 순서대로 담는다
 * @param measurementItems 카테고리에 연결한 순서대로 담는다
 */
public record CategoryInputsResponse(
    String categoryId,
    String categoryName,
    String productTypeId,
    String productTypeName,
    List<InputAttribute> attributes,
    List<Measurement> measurementItems) {

    /**
     * @param inputType 입력 방식의 이름(예: PALETTE)
     * @param role 항목의 역할 이름(OPTION 또는 INFO)
     * @param values 고를 수 있는 값. 직접 입력 항목이면 빈 목록이다
     */
    public record InputAttribute(
        String attributeId,
        String code,
        String name,
        String inputType,
        String role,
        boolean required,
        boolean sizeRole,
        boolean priceAffecting,
        List<SelectableValue> values) {

        static InputAttribute from(CategoryInputsResult.InputAttribute result) {
            List<SelectableValue> values = new ArrayList<>();
            for (CategoryInputsResult.SelectableValue value : result.values()) {
                values.add(new SelectableValue(String.valueOf(value.valueId()), value.value(), value.colorCode()));
            }
            return new InputAttribute(
                String.valueOf(result.attributeId()),
                result.code(),
                result.name(),
                result.inputType(),
                result.role(),
                result.required(),
                result.sizeRole(),
                result.priceAffecting(),
                values);
        }
    }

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record SelectableValue(String valueId, String value, String colorCode) {
    }

    public record Measurement(String code, String name, String unit) {
    }

    public static CategoryInputsResponse from(CategoryInputsResult result) {
        List<InputAttribute> attributes = new ArrayList<>();
        for (CategoryInputsResult.InputAttribute attribute : result.attributes()) {
            attributes.add(InputAttribute.from(attribute));
        }
        List<Measurement> measurementItems = new ArrayList<>();
        for (CategoryInputsResult.Measurement item : result.measurementItems()) {
            measurementItems.add(new Measurement(item.code(), item.name(), item.unit()));
        }
        return new CategoryInputsResponse(
            String.valueOf(result.categoryId()),
            result.categoryName(),
            String.valueOf(result.productTypeId()),
            result.productTypeName(),
            attributes,
            measurementItems);
    }
}
