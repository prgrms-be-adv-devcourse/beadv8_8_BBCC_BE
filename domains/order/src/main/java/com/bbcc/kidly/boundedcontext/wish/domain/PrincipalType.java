package com.bbcc.kidly.boundedcontext.wish.domain;

/**
 * 조르기에 나오는 사람의 종류(#999): 회원(보호자) 또는 로그인한 자녀. 사람은 고정하지 않고 종류 + ID로 둔다
 * (회원이면 회원 ID, 자녀면 자녀 프로필 ID). 장바구니 주인 종류(MEMBER · CHILD, #998)와 같은 값이다.
 */
public enum PrincipalType {
    MEMBER,
    CHILD
}
