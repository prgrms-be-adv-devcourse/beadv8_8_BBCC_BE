package com.bbcc.kidly.boundedcontext.product.app.port.search;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;

/**
 * 상품 목록의 거르기 · 정렬 조건이다.
 *
 * @param categoryId null이면 카테고리로 거르지 않는다
 * @param keyword 상품명에 들어간 글자. null이거나 공백뿐이면 거르지 않는다
 * @param attributeValueIds 항목 code별로 고른 값 ID 목록. 같은 항목 안의 값은 하나만 맞으면 되고, 항목이 다르면 모두 맞아야 한다.
 *     null이면 빈 Map으로 받고, 값 목록이 빈 항목은 거르는 데 쓰지 않는다
 * @param sort 정렬. 반드시 있어야 한다
 */
public record ProductSearchCondition(
    Long categoryId, String keyword, Map<String, List<Long>> attributeValueIds, ProductSearchSort sort) {

    public ProductSearchCondition {
        requireNonNull(sort, "sort는 필수입니다");
        if (attributeValueIds == null) {
            attributeValueIds = Map.of();
        }
    }
}
