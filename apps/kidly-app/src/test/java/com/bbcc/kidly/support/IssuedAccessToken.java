package com.bbcc.kidly.support;

import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.AccessTokenClaims;
import com.bbcc.kidly.shared.auth.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 회원이 발급한 Access Token(로그인 · 재발급 · dev 토큰 응답)을 테스트 공개키로 검증(서명 · 만료)해 읽은 클레임.
 * 서비스는 토큰을 보지 않으므로, 그 회원으로 다른 API를 부를 때는 {@link #headers()}로 Gateway 헤더를 붙인다.
 * 테스트는 루트에서 돈다(kidly.boot-app).
 */
public record IssuedAccessToken(Long memberId, String nickname, List<Role> roles, Instant expiresAt) {

    // application-test.yml의 jwt.public-key와 같은 파일
    private static final JwtDecoder DECODER =
        NimbusJwtDecoder.withPublicKey(readPublicKey(Path.of("config/jwt/local-public.pem"))).build();

    public static IssuedAccessToken decode(String token) {
        var jwt = DECODER.decode(token);
        var roles = jwt.getClaimAsStringList(AccessTokenClaims.ROLES).stream().map(Role::valueOf).toList();
        return new IssuedAccessToken(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(AccessTokenClaims.NICKNAME),
            roles, jwt.getExpiresAt());
    }

    public Consumer<HttpHeaders> headers() {
        return TestMembers.of(memberId, nickname, roles.toArray(Role[]::new));
    }

    private static RSAPublicKey readPublicKey(Path path) {
        try (var inputStream = Files.newInputStream(path)) {
            return RsaKeyConverters.x509().convert(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("테스트 공개키를 읽지 못했습니다: " + path, e);
        }
    }
}
