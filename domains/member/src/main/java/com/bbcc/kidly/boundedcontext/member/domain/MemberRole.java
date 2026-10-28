package com.bbcc.kidly.boundedcontext.member.domain;

// 회원 역할. SELLER는 역할로 저장하지 않는다 (판매자 여부는 Member의 판매자 상태 복사본으로 판단)
// ADMIN은 가입으로 만들 수 없다. 첫 관리자는 시드 데이터로, 그 뒤로는 관리자가 지정 · 해제한다
public enum MemberRole {
    USER,
    ADMIN
}
