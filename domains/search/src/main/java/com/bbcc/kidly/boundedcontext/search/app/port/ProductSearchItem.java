package com.bbcc.kidly.boundedcontext.search.app.port;

/**
 * 상품 목록의 카드 하나다.
 *
 * @param thumbnailUrl 판매자가 썸네일을 등록하지 않았으면 null
 * @param businessName 판매자 문서가 없는 판매자의 상품이면 null
 * @param minPrice 판매할 수 있는 옵션 중 최저가. 판매할 수 있는 옵션이 없으면 전체 옵션 중 최저가다
 * @param soldOut 모든 옵션이 품절이면 true
 */
public record ProductSearchItem(Long productId, String name, String thumbnailUrl, String businessName,
    long minPrice, boolean soldOut, boolean sizeFree) {
}
