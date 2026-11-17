package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

public record AttributeResult(Long attributeId, String name, String code, String inputType, List<Value> values) {

    public record Value(Long valueId, String name, String colorCode) {
    }
}
