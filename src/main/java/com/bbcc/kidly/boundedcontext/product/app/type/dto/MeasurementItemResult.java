package com.bbcc.kidly.boundedcontext.product.app.type.dto;

/**
 * 만든 실측 항목이다.
 *
 * @param unit 실측 단위(예: cm)
 */
public record MeasurementItemResult(Long measurementItemId, String name, String code, String unit) {
}
