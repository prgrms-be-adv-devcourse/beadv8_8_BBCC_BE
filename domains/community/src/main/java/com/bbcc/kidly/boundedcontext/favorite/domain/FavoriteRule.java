package com.bbcc.kidly.boundedcontext.favorite.domain;

/**
 * 찜 규칙 위반의 종류.
 */
public enum FavoriteRule {
    // 판매 종료 상품은 새로 찜할 수 없다
    SALE_ENDED
}
