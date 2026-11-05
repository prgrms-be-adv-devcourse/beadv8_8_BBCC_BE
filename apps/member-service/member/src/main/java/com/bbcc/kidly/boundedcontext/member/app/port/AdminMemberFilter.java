package com.bbcc.kidly.boundedcontext.member.app.port;

/**
 * 관리자 회원 목록 필터. 판매자 여부는 판매자 상태 복사본이 있는지로 본다(판매자 정지 포함).
 */
public enum AdminMemberFilter {
    ALL,
    BUYER,
    SELLER,
    SUSPENDED
}
