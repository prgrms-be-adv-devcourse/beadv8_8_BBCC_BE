package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 카테고리 계층의 한 칸이다.
 *
 * @param leaf 상품 유형을 가진 말단 카테고리이면 true
 * @param children 하위 카테고리를 ID 순서로 담는다. 하위가 없으면 빈 목록이다
 */
public record CategoryTreeResult(Long categoryId, String name, boolean leaf, List<CategoryTreeResult> children) {
}
