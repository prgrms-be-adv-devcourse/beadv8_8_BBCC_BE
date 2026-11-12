package com.bbcc.kidly.shared.community.event;

/**
 * 찜이 지워진 까닭.
 */
public enum FavoriteRemovalCause {
    // 회원이 찜을 해제했다
    USER,
    // 회원 탈퇴로 그 회원의 찜을 지웠다
    MEMBER_WITHDRAWN
}
