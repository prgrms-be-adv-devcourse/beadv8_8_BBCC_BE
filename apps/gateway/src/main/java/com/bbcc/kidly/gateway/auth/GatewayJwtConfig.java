package com.bbcc.kidly.gateway.auth;

import com.bbcc.kidly.gateway.revocation.AccessTokenRevocations;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * 공개키로 서명을 확인하고, 기본 검사(만료 등, 시계 오차 60초) 뒤 무효화된 주체인지 본다.
 * 무효화 목록은 Redis에서 읽어(비동기) 동기 검사기(OAuth2TokenValidator)에 넣지 못하므로 디코더를 감싼다.
 */
@Configuration
class GatewayJwtConfig {

    // 보존 기간(토큰 유효 기간 + 시계 오차)은 토큰 설정에서 나온다. 리스너(revocation)는 이 빈에서 되감을 기간을 얻는다
    @Bean
    AccessTokenRevocations accessTokenRevocations(GatewayJwtProperties jwtProperties,
        ReactiveStringRedisTemplate redis) {
        return new AccessTokenRevocations(redis, jwtProperties.revocationRetention());
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayJwtProperties jwtProperties, AccessTokenRevocations revocations) {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(jwtProperties.rsaPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
            new JwtTimestampValidator(GatewayJwtProperties.CLOCK_SKEW)));
        decoder.setClaimSetConverter(requireIssuedAt());
        return token -> decoder.decode(token).flatMap(jwt -> notRevoked(jwt, revocations));
    }

    // 무효화는 발급 시각(iat)과 비교한다. 기본 변환기는 iat가 없으면 exp − 1초로 채워 무효화를 건너뛰게 하므로 변환 전에 거절한다
    private static Converter<Map<String, Object>, Map<String, Object>> requireIssuedAt() {
        var defaults = MappedJwtClaimSetConverter.withDefaults(Map.of());
        return claims -> {
            if (!claims.containsKey(JwtClaimNames.IAT)) {
                throw new BadJwtException("발급 시각이 없는 Access Token입니다");
            }
            return defaults.convert(claims);
        };
    }

    private static Mono<Jwt> notRevoked(Jwt jwt, AccessTokenRevocations revocations) {
        return revocations.isRevoked(principal(jwt), jwt.getIssuedAt(), Instant.now())
            .flatMap(revoked -> revoked
                ? Mono.<Jwt>error(new BadJwtException("무효화된 Access Token입니다"))
                : Mono.just(jwt));
    }

    private static PrincipalId principal(Jwt jwt) {
        try {
            return PrincipalId.parseSubject(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("잘못된 토큰 주체입니다", e);
        }
    }
}
