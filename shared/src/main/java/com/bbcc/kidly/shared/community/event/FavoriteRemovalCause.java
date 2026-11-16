package com.bbcc.kidly.shared.community.event;

/**
 * 찜이 지워진 까닭.
 */
public enum FavoriteRemovalCause {
    // 회원 · 자녀 계정이 찜을 해제했다
    USER,
    // 회원 탈퇴로 그 회원(본인 · 자녀 계정)의 찜을 지웠거나, 자녀 계정 삭제로 그 계정의 찜을 지웠다(ownerType CHILD)
    MEMBER_WITHDRAWN
}
