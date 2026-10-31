package com.bbcc.kidly.gateway.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * JWT 공개키(PEM, X.509 PUBLIC KEY)와 Access Token 유효 기간. 개인키는 회원만 가진다.
 * 값은 kidly-app(auth.yml)과 같아야 한다.
 */
@ConfigurationProperties("jwt")
public record GatewayJwtProperties(Resource publicKey, Duration accessTokenValidity) {

    // JWT 만료 검증이 허용하는 시계 오차. common JwtProperties.CLOCK_SKEW와 같다
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // 무효화 기록이 의미 있는 기간. 토큰은 만료 + 시계 오차까지 통과하므로 그만큼 들고 있는다
    public Duration revocationRetention() {
        return accessTokenValidity.plus(CLOCK_SKEW);
    }

    public RSAPublicKey rsaPublicKey() {
        try (var inputStream = publicKey.getInputStream()) {
            return RsaKeyConverters.x509().convert(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("JWT 공개키를 읽지 못했습니다: " + publicKey, e);
        }
    }
}
