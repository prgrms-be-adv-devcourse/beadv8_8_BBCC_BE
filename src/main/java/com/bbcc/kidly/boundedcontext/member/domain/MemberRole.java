package com.bbcc.kidly.boundedcontext.member.domain;

// 회원 역할. SELLER는 역할로 저장하지 않는다 (판매자 여부는 Member의 판매자 상태 복사본으로 판단). ADMIN은 설정의 관리자 이메일로 정해진다 (AdminProperties)
public enum MemberRole {
    USER,
    ADMIN
}
