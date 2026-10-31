package com.bbcc.kidly.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 무효화된 회원의 Access Token이면 거절한다. 서명 검증 뒤 기본 검사(만료 등)와 함께 실행된다.
 * jwt 모드에서만 쓰므로 빈으로 두지 않고 SecurityConfig의 jwtDecoder가 만든다(gateway 모드 서비스는 AccessTokenRevocations가 없어도 뜬다).
 */
@RequiredArgsConstructor
public class RevokedAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "무효화된 Access Token입니다", null);

    private final AccessTokenRevocations revocations;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        var memberId = Long.valueOf(jwt.getSubject());
        return revocations.isRevoked(memberId, jwt.getIssuedAt())
            ? OAuth2TokenValidatorResult.failure(REVOKED)
            : OAuth2TokenValidatorResult.success();
    }
}
