package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

/**
 * 회원이 남긴 활동의 종류다. 활동 종류가 늘면 값을 더한다.
 */
public enum ActivityType {
    SEARCH,
    CART_ITEM_ADDED,
    CART_ITEM_REMOVED,
    PRODUCT_VIEWED,
    SEARCH_RESULT_CLICKED,
    WISH_SENT,
    WISH_CANCELLED,
    FAVORITE_ADDED,
    FAVORITE_REMOVED
}
