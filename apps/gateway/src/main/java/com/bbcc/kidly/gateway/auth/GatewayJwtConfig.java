package com.bbcc.kidly.gateway.auth;

import com.bbcc.kidly.gateway.revocation.AccessTokenRevocations;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * 공개키로 서명을 확인하고, 기본 검사(만료 등, 시계 오차 60초) 뒤 무효화된 회원인지 본다. common SecurityConfig와 같은 규칙이다.
 */
@Configuration
class GatewayJwtConfig {

    private static final OAuth2Error REVOKED =
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "무효화된 Access Token입니다", null);

    // 보존 기간(토큰 유효 기간 + 시계 오차)은 토큰 설정에서 나온다. 리스너(revocation)는 이 빈에서 되감을 기간을 얻는다
    @Bean
    AccessTokenRevocations accessTokenRevocations(GatewayJwtProperties jwtProperties) {
        return new AccessTokenRevocations(jwtProperties.revocationRetention());
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayJwtProperties jwtProperties, AccessTokenRevocations revocations) {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(jwtProperties.rsaPublicKey()).build();
        OAuth2TokenValidator<Jwt> notRevoked = jwt ->
            revocations.isRevoked(Long.valueOf(jwt.getSubject()), jwt.getIssuedAt(), Instant.now())
                ? OAuth2TokenValidatorResult.failure(REVOKED)
                : OAuth2TokenValidatorResult.success();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
            new JwtTimestampValidator(GatewayJwtProperties.CLOCK_SKEW), notRevoked));
        return decoder;
    }
}
