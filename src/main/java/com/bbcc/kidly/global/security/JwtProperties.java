package com.bbcc.kidly.global.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * JWT 서명 키(PEM 파일 위치)와 Access Token 유효 기간.
 * 공개키는 PUBLIC KEY(X.509), 개인키는 PRIVATE KEY(PKCS#8) 형식이어야 한다.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(Resource publicKey, Resource privateKey, Duration accessTokenValidity) {

    // JWT 만료 검증이 허용하는 시계 오차. SecurityConfig와 무효화 보존 기간이 함께 쓴다
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    // 무효화 기록이 의미 있는 기간. 토큰은 만료 + 시계 오차까지 통과하므로 그만큼 들고 있는다
    public Duration revocationRetention() {
        return accessTokenValidity.plus(CLOCK_SKEW);
    }

    public RSAPublicKey rsaPublicKey() {
        return read(publicKey, RsaKeyConverters.x509());
    }

    public RSAPrivateKey rsaPrivateKey() {
        return read(privateKey, RsaKeyConverters.pkcs8());
    }

    private static <T> T read(Resource resource, Converter<InputStream, T> converter) {
        try (InputStream inputStream = resource.getInputStream()) {
            return converter.convert(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("JWT 키를 읽지 못했습니다: " + resource, e);
        }
    }
}
