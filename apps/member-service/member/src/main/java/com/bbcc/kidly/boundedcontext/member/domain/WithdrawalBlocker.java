package com.bbcc.kidly.boundedcontext.member.domain;

// 판매자 탈퇴를 막는 이유. type은 제공하는 쪽이 새 값을 더할 수 있어 문자열로 둔다
public record WithdrawalBlocker(String type, long count) {

    // 주문은 개수만 주므로 회원이 이 이름으로 바꾼다
    public static final String UNCONFIRMED_ORDER_ITEM = "UNCONFIRMED_ORDER_ITEM";
}
