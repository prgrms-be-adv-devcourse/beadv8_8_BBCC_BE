package com.bbcc.kidly.shared.auth;

/**
 * 회원이 발급하고 Gateway가 읽는 Access Token의 클레임 이름. sub는 회원 ID다.
 */
public final class AccessTokenClaims {

    // 역할 이름 목록({@link Role#name()})
    public static final String ROLES = "roles";
    // 발급할 때의 닉네임. 배포 전에 발급한 토큰에는 없다
    public static final String NICKNAME = "nickname";

    private AccessTokenClaims() {
    }
}
