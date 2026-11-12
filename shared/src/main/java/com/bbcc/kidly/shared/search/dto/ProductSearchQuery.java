package com.bbcc.kidly.shared.search.dto;

/**
 * 검색어로 판매중 상품을 찾는 조건(#1191). 관련도순(검색어 + 의미 검색)으로 찾는다. 회원 정보는 담지 않는다.
 *
 * @param keyword 검색어(필수, 공백만은 안 됨, 50자 이하)
 * @param minPrice 표시 가격(원) 하한. null이면 거르지 않는다
 * @param maxPrice 표시 가격(원) 상한. null이면 거르지 않는다
 * @param limit 가져올 상품 수(1~10)
 */
public record ProductSearchQuery(String keyword, Long minPrice, Long maxPrice, int limit) {
}
