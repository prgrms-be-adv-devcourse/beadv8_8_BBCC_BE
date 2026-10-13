package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;

/**
 * 소셜 제공자에서 인가 코드로 회원 정보를 받는다.
 * 잘못된 인가 코드면 {@code INVALID_AUTHORIZATION_CODE}, 제공자 오류면 {@code OAUTH_PROVIDER_ERROR}로
 * {@code BusinessException}을 던진다.
 */
public interface OAuthClient {

    OAuthProfile fetchProfile(OAuthProvider provider, String code);
}
