package com.bbcc.kidly.boundedcontext.order.support;

import com.bbcc.kidly.global.security.JwtProperties;
import com.bbcc.kidly.shared.auth.Role;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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
 * 주문 기능 테스트에서 로그인한 회원의 Access Token을 만든다(#616). 예: {@code headers.setBearerAuth(tokens.buyer(1L))}
 * 회원 도메인 코드를 쓰지 않고 common JwtProperties의 테스트 키로 직접 서명한다. 클레임은 common AuthMember가 읽는 것
 * (sub = 회원 ID, nickname, roles)과 발급 · 만료 시각이다.
 */
@Component
public class TestTokens {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public TestTokens(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        RSAKey key = new RSAKey.Builder(jwtProperties.rsaPublicKey()).privateKey(jwtProperties.rsaPrivateKey()).build();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    public static String nickname(Long memberId) {
        return "회원" + memberId;
    }

    public String buyer(Long memberId) {
        return issue(memberId, List.of(Role.BUYER));
    }

    public String seller(Long memberId) {
        return issue(memberId, List.of(Role.BUYER, Role.SELLER));
    }

    public String admin(Long memberId) {
        return issue(memberId, List.of(Role.BUYER, Role.ADMIN));
    }

    private String issue(Long memberId, List<Role> roles) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(String.valueOf(memberId))
            .claim("nickname", nickname(memberId))
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTokenValidity()))
            .claim("roles", roles.stream().map(Role::name).toList())
            .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
