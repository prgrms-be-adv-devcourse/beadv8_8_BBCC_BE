package com.bbcc.kidly.boundedcontext.product.domain.type;

/**
 * 판매자가 항목 값을 넣는 방식이다. 직접 입력만 글자로 적고, 나머지는 정해 둔 값에서 고른다.
 */
public enum AttributeInputType {
    PALETTE,
    LIST,
    MULTI_LIST,
    TEXT
}
