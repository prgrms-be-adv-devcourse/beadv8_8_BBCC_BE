package com.bbcc.kidly.boundedcontext.member.domain;

// 회원 역할. SELLER는 저장하지 않는다 (판매자 여부는 Seller로 판단). ADMIN은 시드 데이터로만 부여한다
public enum MemberRole {
    USER,
    ADMIN
}
