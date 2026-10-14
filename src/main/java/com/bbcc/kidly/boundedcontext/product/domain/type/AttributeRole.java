package com.bbcc.kidly.boundedcontext.product.domain.type;

/**
 * 상품 유형 안에서 항목이 맡는 역할이다. 옵션 항목은 구매자가 골라서 사는 값(색상, 사이즈)이고,
 * 정보 항목은 판매자가 상품 설명으로 적는 값(주요소재, 혼용률)이다.
 */
public enum AttributeRole {
    OPTION,
    INFO
}
