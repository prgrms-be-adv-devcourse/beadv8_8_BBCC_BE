package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

/**
 * 카테고리 경로의 한 칸이다. 기준 옷은 상품 카테고리 경로를 그대로 받아 세부 종류를 확인한다.
 */
public record GarmentCategoryPathEntry(Long categoryId, String categoryName) {
}
