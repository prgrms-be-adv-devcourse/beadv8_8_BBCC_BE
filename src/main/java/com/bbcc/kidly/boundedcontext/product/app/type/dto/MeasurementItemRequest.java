package com.bbcc.kidly.boundedcontext.product.app.type.dto;

/**
 * 관리자가 실측 항목을 만들 때 보내는 요청이다.
 *
 * @param unit 실측 단위(예: cm)
 */
public record MeasurementItemRequest(String name, String code, String unit) {
}
