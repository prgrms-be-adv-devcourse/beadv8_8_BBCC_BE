package com.bbcc.kidly.boundedcontext.product.in.type.dto;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @param categoryId 카테고리 ID. API 규칙대로 문자열이다
 * @param leaf 상품을 등록할 수 있는 말단 카테고리이면 true
 * @param children 하위 카테고리. 하위가 없으면 빈 목록이다
 */
public record CategoryTreeResponse(String categoryId, String name, boolean leaf, List<CategoryTreeResponse> children) {

    public static CategoryTreeResponse from(CategoryTreeResult result) {
        List<CategoryTreeResponse> children = new ArrayList<>();
        for (CategoryTreeResult child : result.children()) {
            children.add(from(child));
        }
        return new CategoryTreeResponse(String.valueOf(result.categoryId()), result.name(), result.leaf(), children);
    }
}
