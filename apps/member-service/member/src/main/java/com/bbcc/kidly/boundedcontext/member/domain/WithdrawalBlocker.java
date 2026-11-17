package com.bbcc.kidly.boundedcontext.member.domain;

public record WithdrawalBlocker(String type, long count) {

    public static final String UNCONFIRMED_ORDER_ITEM = "UNCONFIRMED_ORDER_ITEM";
}
