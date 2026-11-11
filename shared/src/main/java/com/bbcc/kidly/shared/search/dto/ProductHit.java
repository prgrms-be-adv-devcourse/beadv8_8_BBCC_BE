package com.bbcc.kidly.shared.search.dto;

/**
 * 검색으로 찾은 상품 하나(#1191). 상품 목록 카드와 같은 값이다.
 *
 * @param price 표시 가격(원): 판매할 수 있는 옵션 중 최저가, 없으면 전체 옵션 중 최저가
 * @param businessName 판매자 상호명. 모르면 null
 * @param thumbnailUrl 썸네일이 없으면 null
 * @param soldOut 모든 옵션이 품절이면 true
 */
public record ProductHit(Long productId, String name, String businessName, Long price, String thumbnailUrl,
    boolean soldOut) {
}
