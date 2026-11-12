package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import java.util.ArrayList;
import java.util.List;

/**
 * @param parentId 최상위 카테고리이면 null
 * @param productTypeId 중간 카테고리이면 null
 * @param productTypeName 중간 카테고리이면 null
 * @param visibleGender 이 카테고리를 보여 줄 자녀 성별이다. ALL · GIRL
 */
public record CategoryResponse(
    String categoryId,
    String name,
    String parentId,
    boolean leaf,
    String productTypeId,
    String productTypeName,
    CategoryVisibleGender visibleGender,
    List<MeasurementItemResponse> measurementItems) {

    public static CategoryResponse from(CategoryResult result) {
        List<MeasurementItemResponse> measurementItems = new ArrayList<>();
        for (MeasurementItemResult item : result.measurementItems()) {
            measurementItems.add(MeasurementItemResponse.from(item));
        }
        return new CategoryResponse(
            String.valueOf(result.categoryId()),
            result.name(),
            idOrNull(result.parentId()),
            result.leaf(),
            idOrNull(result.productTypeId()),
            result.productTypeName(),
            result.visibleGender(),
            measurementItems);
    }

    // 없는 ID가 문자열 "null"이 되지 않게 null로 둔다
    private static String idOrNull(Long id) {
        if (id == null) {
            return null;
        }
        return String.valueOf(id);
    }
}
