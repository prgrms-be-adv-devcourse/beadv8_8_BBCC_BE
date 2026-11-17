package com.bbcc.kidly.support;

import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.member.support.security.TestChildren;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.auth.AccessTokenClaims;
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

public record IssuedAccessToken(PrincipalId principal, String nickname, List<Role> roles, Instant expiresAt,
    Long guardianId) {

    private static final JwtDecoder DECODER =
        NimbusJwtDecoder.withPublicKey(readPublicKey(Path.of("config/jwt/local-public.pem"))).build();

    public static IssuedAccessToken decode(String token) {
        var jwt = DECODER.decode(token);
        var roles = Optional.ofNullable(jwt.getClaimAsStringList(AccessTokenClaims.ROLES)).orElse(List.of())
            .stream().map(Role::valueOf).toList();
        var guardianId = Optional.ofNullable((Number) jwt.getClaim(AccessTokenClaims.GUARDIAN_ID))
            .map(Number::longValue)
            .orElse(null);
        return new IssuedAccessToken(PrincipalId.parseSubject(jwt.getSubject()),
            jwt.getClaimAsString(AccessTokenClaims.NICKNAME), roles, jwt.getExpiresAt(), guardianId);
    }

    public Consumer<HttpHeaders> headers() {
        return switch (principal.type()) {
            case MEMBER -> TestMembers.of(principal.id(), nickname, roles.toArray(Role[]::new));
            case ADMIN -> TestAdmins.of(principal.id());
            case CHILD -> TestChildren.of(principal.id(), guardianId);
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
