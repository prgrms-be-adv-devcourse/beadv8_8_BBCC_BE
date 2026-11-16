package com.bbcc.kidly.boundedcontext.favorite.domain;

/**
 * 찜 소유자 종류. 이름은 표 칸(owner_type)과 찜 이벤트의 ownerType 값이다.
 */
public enum FavoriteOwnerType {
    // 회원 본인. 소유자 ID = 회원 ID
    MEMBER,
    // 자녀 계정. 소유자 ID = 자녀 계정 ID(토큰 X-Child-Account-Id)
    CHILD
}
