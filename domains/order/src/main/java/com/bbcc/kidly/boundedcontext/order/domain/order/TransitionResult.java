package com.bbcc.kidly.boundedcontext.order.domain.order;

/**
 * 주문 상태 전이 결과. 바뀌지 않았을 때 "이미 처리됨"과 "적용할 수 없음"을 구분한다.
 */
public enum TransitionResult {

    /** 이번 요청으로 상태가 바뀌었다. */
    APPLIED,

    /** 이미 그 상태라 그대로 두었다(중복 요청). */
    ALREADY_DONE,

    /** 다른 상태이거나 조건을 채우지 못해 적용하지 않았다. */
    NOT_APPLICABLE
}
