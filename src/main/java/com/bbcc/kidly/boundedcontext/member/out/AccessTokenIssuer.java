package com.bbcc.kidly.boundedcontext.member.out;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 회원 ID와 역할로 Access Token(JWT, RS256)을 발급한다. 개인키는 회원 도메인만 가진다.
 * JWT의 iat·exp는 epoch 초라서 LocalDateTime 대신 Instant를 쓴다.
 */
@Component
public class AccessTokenIssuer {

    private static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final Duration validity;

    public AccessTokenIssuer(
        @Value("${jwt.public-key}") RSAPublicKey publicKey,
        @Value("${jwt.private-key}") RSAPrivateKey privateKey,
        @Value("${jwt.access-token-validity}") Duration validity) {
        this.jwtEncoder = NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build();
        this.validity = validity;
    }

    public String issue(Long memberId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(String.valueOf(memberId))
            .issuedAt(now)
            .expiresAt(now.plus(validity))
            .claim(ROLES_CLAIM, roles)
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
