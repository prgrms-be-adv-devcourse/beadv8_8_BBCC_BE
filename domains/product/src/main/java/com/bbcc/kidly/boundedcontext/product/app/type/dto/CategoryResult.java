package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import java.util.List;

public record CategoryResult(
    Long categoryId,
    String name,
    Long parentId,
    boolean leaf,
    Long productTypeId,
    String productTypeName,
    CategoryVisibleGender visibleGender,
    List<MeasurementItemResult> measurementItems) {
}
