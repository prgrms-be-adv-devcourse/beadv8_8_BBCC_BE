package com.bbcc.kidly.support;

import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.auth.AccessTokenClaims;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 인증이 발급한 Access Token(로그인 · 재발급 · dev 토큰 응답)을 테스트 공개키로 검증(서명 · 만료)해 읽은 클레임.
 * 테스트는 루트에서 돈다(kidly.boot-app).
 */
public record IssuedAccessToken(PrincipalId principal, String nickname, List<Role> roles, Instant expiresAt,
    Long guardianId) {

    // application-test.yml의 jwt.public-key와 같은 파일
    private static final JwtDecoder DECODER =
        NimbusJwtDecoder.withPublicKey(readPublicKey(Path.of("config/jwt/local-public.pem"))).build();

    public static IssuedAccessToken decode(String token) {
        var jwt = DECODER.decode(token);
        var roles = Optional.ofNullable(jwt.getClaimAsStringList(AccessTokenClaims.ROLES)).orElse(List.of())
            .stream().map(Role::valueOf).toList();
        return new IssuedAccessToken(PrincipalId.parseSubject(jwt.getSubject()),
            jwt.getClaimAsString(AccessTokenClaims.NICKNAME), roles, jwt.getExpiresAt(),
            Optional.ofNullable((Number) jwt.getClaim(AccessTokenClaims.GUARDIAN_ID))
                .map(Number::longValue).orElse(null));
    }

    public Consumer<HttpHeaders> headers() {
        return switch (principal.type()) {
            case MEMBER -> TestMembers.of(principal.id(), nickname, roles.toArray(Role[]::new));
            case ADMIN -> headers -> headers.set(PrincipalHeaders.ADMIN_ID, String.valueOf(principal.id()));
            case CHILD -> headers -> {
                headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(principal.id()));
                headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(guardianId));
            };
        };
    }

    private static RSAPublicKey readPublicKey(Path path) {
        try (var inputStream = Files.newInputStream(path)) {
            return RsaKeyConverters.x509().convert(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("테스트 공개키를 읽지 못했습니다: " + path, e);
        }
    }
}
