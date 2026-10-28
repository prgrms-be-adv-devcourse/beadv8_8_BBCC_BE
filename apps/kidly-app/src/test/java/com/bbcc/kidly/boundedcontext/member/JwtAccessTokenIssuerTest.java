package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.out.JwtAccessTokenIssuer;
import com.bbcc.kidly.global.security.JwtProperties;
import com.bbcc.kidly.global.security.Role;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtAccessTokenIssuerTest {

    private static final Duration VALIDITY = Duration.ofMinutes(30);

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final JwtAccessTokenIssuer ISSUER = new JwtAccessTokenIssuer(new JwtProperties(
        pem("PUBLIC KEY", KEY_PAIR.getPublic()), pem("PRIVATE KEY", KEY_PAIR.getPrivate()), VALIDITY));

    @Test
    @DisplayName("회원 ID를 sub에, 닉네임을 nickname에, 역할을 roles에 담는다")
    void claims() {
        String token = ISSUER.issue(1L, "소소맘", List.of(Role.BUYER, Role.SELLER));

        Jwt jwt = decoderOf(KEY_PAIR).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString("nickname")).isEqualTo("소소맘");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("BUYER", "SELLER");
    }

    @Test
    @DisplayName("업무 정보는 sub · nickname · roles만 담는다")
    void onlyAllowedClaims() {
        String token = ISSUER.issue(1L, "소소맘", List.of(Role.BUYER));

        Jwt jwt = decoderOf(KEY_PAIR).decode(token);

        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "nickname", "roles", "iat", "exp");
    }

    @Test
    @DisplayName("만료 시각은 발급 시각에서 유효 기간만큼 뒤다")
    void validity() {
        String token = ISSUER.issue(1L, "소소맘", List.of(Role.BUYER));

        Jwt jwt = decoderOf(KEY_PAIR).decode(token);

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(VALIDITY);
    }

    @Test
    @DisplayName("RS256으로 서명한다")
    void algorithm() {
        String token = ISSUER.issue(1L, "소소맘", List.of(Role.BUYER));

        Jwt jwt = decoderOf(KEY_PAIR).decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    @DisplayName("다른 공개키로는 검증되지 않는다")
    void otherKeyRejected() {
        String token = ISSUER.issue(1L, "소소맘", List.of(Role.BUYER));

        JwtDecoder otherDecoder = decoderOf(generateKeyPair());

        assertThatThrownBy(() -> otherDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static JwtDecoder decoderOf(KeyPair keyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }

    // 설정 파일과 같은 PEM 형식으로 만들어 JwtProperties가 키를 읽는 과정까지 거치게 한다
    private static Resource pem(String type, Key key) {
        String body = Base64.getMimeEncoder().encodeToString(key.getEncoded());
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
