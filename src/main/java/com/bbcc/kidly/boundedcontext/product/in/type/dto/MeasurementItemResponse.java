package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;

/**
 * @param unit 실측 단위(예: cm)
 */
public record MeasurementItemResponse(String measurementItemId, String name, String code, String unit) {

    public static MeasurementItemResponse from(MeasurementItemResult result) {
        return new MeasurementItemResponse(
            String.valueOf(result.measurementItemId()), result.name(), result.code(), result.unit());
    }
}
