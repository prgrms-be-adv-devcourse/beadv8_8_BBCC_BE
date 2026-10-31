package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.global.security.JwtProperties;
import com.bbcc.kidly.shared.auth.AccessTokenClaims;
import com.bbcc.kidly.shared.auth.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 회원 ID · 닉네임 · 역할로 Access Token(JWT, RS256)을 발급한다. 개인키는 회원 도메인만 가진다.
 * 닉네임은 화면 표시 · 신청 스냅샷용이다.
 * JWT의 iat·exp는 epoch 초라서 LocalDateTime 대신 Instant를 쓴다.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Duration validity;

    public JwtAccessTokenIssuer(JwtProperties jwtProperties) {
        this.jwtEncoder = NimbusJwtEncoder
            .withKeyPair(jwtProperties.rsaPublicKey(), jwtProperties.rsaPrivateKey())
            .build();
        this.validity = jwtProperties.accessTokenValidity();
    }

    @Override
    public String issue(Long memberId, String nickname, List<Role> roles) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(String.valueOf(memberId))
            .claim(AccessTokenClaims.NICKNAME, nickname)
            .issuedAt(now)
            .expiresAt(now.plus(validity))
            .claim(AccessTokenClaims.ROLES, roles.stream().map(Role::name).toList())
            .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
