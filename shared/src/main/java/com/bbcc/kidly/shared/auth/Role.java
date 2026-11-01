package com.bbcc.kidly.shared.auth;

/**
 * Access Token의 roles 클레임 값. Gateway가 X-Member-Roles 헤더로 넘기고, 관리자 · 판매자 API의 역할은 Gateway가 검사한다.
 * 회원 테이블에 저장하는 MemberRole과 다르다. 판매자 여부는 저장하지 않고 토큰을 발급할 때 계산한다.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN
}
