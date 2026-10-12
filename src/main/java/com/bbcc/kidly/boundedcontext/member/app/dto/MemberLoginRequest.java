package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;

/**
 * 소셜 로그인으로 받은 회원 정보. 처음 로그인이면 이 값으로 가입한다.
 */
public record MemberLoginRequest(OAuthProvider provider, String providerUserId, String nickname) {
}
