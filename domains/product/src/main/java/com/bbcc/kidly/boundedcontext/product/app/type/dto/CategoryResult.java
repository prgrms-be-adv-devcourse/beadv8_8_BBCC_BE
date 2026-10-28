package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 만든 카테고리와 그 상품 유형, 필수 실측 항목이다.
 *
 * @param parentId 최상위 카테고리이면 null
 * @param productTypeId 중간 카테고리이면 null
 * @param productTypeName 중간 카테고리이면 null
 * @param measurementItems 필수 실측 항목을 순서대로 담는다. 중간 카테고리이면 빈 목록이다
 */
public record CategoryResult(
    Long categoryId,
    String name,
    Long parentId,
    boolean leaf,
    Long productTypeId,
    String productTypeName,
    List<MeasurementItemResult> measurementItems) {
}
