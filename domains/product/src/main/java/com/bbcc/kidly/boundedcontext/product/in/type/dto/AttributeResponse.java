package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.AttributeResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @param inputType 입력 방식의 이름(예: PALETTE)
 */
public record AttributeResponse(
    String attributeId, String name, String code, String inputType, List<Value> values) {

    /**
     * @param colorCode 팔레트 값이 아니면 null
     */
    public record Value(String valueId, String name, String colorCode) {

        public static Value from(AttributeResult.Value result) {
            return new Value(String.valueOf(result.valueId()), result.name(), result.colorCode());
        }
    }

    public static AttributeResponse from(AttributeResult result) {
        List<Value> values = new ArrayList<>();
        for (AttributeResult.Value value : result.values()) {
            values.add(Value.from(value));
        }
        return new AttributeResponse(
            String.valueOf(result.attributeId()), result.name(), result.code(), result.inputType(), values);
    }
}
