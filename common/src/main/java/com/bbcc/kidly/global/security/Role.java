package com.bbcc.kidly.global.security;

/**
 * Access Token의 roles 클레임 값. 요청마다 ROLE_ 권한으로 바뀐다 ({@code hasRole("SELLER")}).
 * 회원 테이블에 저장하는 MemberRole과 다르다. 판매자 여부는 저장하지 않고 토큰을 발급할 때 계산한다.
 */
public enum Role {
    BUYER,
    SELLER,
    ADMIN
}
