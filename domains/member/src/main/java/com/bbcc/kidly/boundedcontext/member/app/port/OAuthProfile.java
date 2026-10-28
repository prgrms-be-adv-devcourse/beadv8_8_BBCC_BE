package com.bbcc.kidly.boundedcontext.member.app.port;

/**
 * 소셜 제공자에서 받은 회원 정보.
 *
 * @param nickname 사용자가 제공에 동의하지 않았으면 null
 * @param email 제공자가 주지 않았거나 유효하지 않거나 인증되지 않았으면 null
 */
public record OAuthProfile(String providerUserId, String nickname, String email) {
}
