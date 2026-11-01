package com.bbcc.kidly.gateway.support;

import com.bbcc.kidly.shared.auth.AccessTokenClaims;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 회원(JwtAccessTokenIssuer)과 같은 모양의 Access Token을 만든다: RS256, sub = 회원 ID, roles = 역할 이름 목록,
 * nickname = 닉네임(issueWithNickname만 넣는다).
 * 로컬 키(config/jwt)로 서명하므로 테스트 설정의 공개키로 검증된다. 테스트는 루트에서 돈다(kidly.boot-app).
 */
public final class TestTokens {

    private static final JwtEncoder LOCAL = NimbusJwtEncoder
        .withKeyPair(read("config/jwt/local-public.pem", RsaKeyConverters.x509()),
            read("config/jwt/local-private.pem", RsaKeyConverters.pkcs8()))
        .build();
    private static final Duration VALIDITY = Duration.ofMinutes(30);

    private TestTokens() {
    }

    public static String issue(long memberId, List<String> roles, Instant issuedAt, Instant expiresAt) {
        return encode(LOCAL, String.valueOf(memberId), roles, null, issuedAt, expiresAt);
    }

    // 회원이 발급하는 토큰처럼 nickname 클레임을 넣는다
    public static String issueWithNickname(long memberId, String nickname) {
        var now = Instant.now();
        return encode(LOCAL, String.valueOf(memberId), List.of("BUYER"), nickname, now, now.plus(VALIDITY));
    }

    // 회원이 만들지 않는 sub(숫자가 아닌 값 등)로 서명한 토큰
    public static String issueWithSubject(String subject, Instant issuedAt, Instant expiresAt) {
        return encode(LOCAL, subject, List.of("BUYER"), null, issuedAt, expiresAt);
    }

    // 모양은 같지만 Gateway가 모르는 키로 서명한 토큰
    public static String issueWithOtherKey(long memberId) {
        var now = Instant.now();
        return encode(otherKeyEncoder(), String.valueOf(memberId), List.of("BUYER"), null, now, now.plus(VALIDITY));
    }

    private static String encode(JwtEncoder encoder, String subject, List<String> roles, String nickname,
        Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(AccessTokenClaims.ROLES, roles);
        if (nickname != null) {
            claims.claim(AccessTokenClaims.NICKNAME, nickname);
        }
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static JwtEncoder otherKeyEncoder() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA 키 쌍을 만들지 못했습니다", e);
        }
    }

    private static <T> T read(String path, Converter<InputStream, T> converter) {
        try (var inputStream = new FileSystemResource(path).getInputStream()) {
            return converter.convert(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("테스트 키를 읽지 못했습니다: " + path, e);
        }
    }
}
