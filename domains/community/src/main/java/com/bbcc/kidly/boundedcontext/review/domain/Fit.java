package com.bbcc.kidly.boundedcontext.review.domain;

/**
 * 리뷰 사이즈 핏: 구매한 사이즈가 어땠는지. 선택 값이다(없으면 null).
 */
public enum Fit {
    // 작아요
    SMALL,
    // 잘 맞아요
    TRUE_TO_SIZE,
    // 커요
    LARGE
}
