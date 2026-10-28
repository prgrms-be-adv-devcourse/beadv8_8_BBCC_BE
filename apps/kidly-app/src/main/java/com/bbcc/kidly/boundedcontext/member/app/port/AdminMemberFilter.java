package com.bbcc.kidly.boundedcontext.member.app.port;

/**
 * 관리자 회원 목록 필터. 판매자 여부는 판매자 상태 복사본이 있는지로 본다(판매자 정지 포함).
 * 관리자는 ADMIN에서만 나오고, 나머지 필터는 일반 회원만 거른다.
 */
public enum AdminMemberFilter {
    ALL,
    BUYER,
    SELLER,
    SUSPENDED,
    ADMIN
}
