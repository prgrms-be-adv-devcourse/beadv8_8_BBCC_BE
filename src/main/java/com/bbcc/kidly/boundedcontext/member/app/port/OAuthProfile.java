package com.bbcc.kidly.boundedcontext.member.app.port;

/**
 * 소셜 제공자에서 받은 회원 정보.
 *
 * @param nickname 사용자가 제공에 동의하지 않았으면 null
 */
public record OAuthProfile(String providerUserId, String nickname) {
}
