package com.bbcc.kidly.boundedcontext.product.domain.type;

/**
 * 판매자가 항목 값을 넣는 방식이다. 팔레트 · 목록 · 다중 선택 목록은 Kidly가 정해 둔 값에서 고르고, 직접 입력은 글자로 적는다.
 */
public enum AttributeInputType {
    PALETTE,
    LIST,
    MULTI_LIST,
    TEXT
}
