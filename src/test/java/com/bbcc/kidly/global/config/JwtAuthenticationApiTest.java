package com.bbcc.kidly.global.config;

import static com.bbcc.kidly.support.TestApiController.ADMIN_PATH;
import static com.bbcc.kidly.support.TestApiController.AUTH_PATH;
import static com.bbcc.kidly.support.TestApiController.SELLER_PATH;

import com.bbcc.kidly.boundedcontext.member.out.AccessTokenIssuer;
import com.bbcc.kidly.support.FunctionalTest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class JwtAuthenticationApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("유효한 토큰이면 로그인이 필요한 API를 호출할 수 있다")
    void validToken() {
        expectOk(AUTH_PATH, accessTokenIssuer.issue(1L, List.of("BUYER")));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 ProblemDetail로 응답한다")
    void expiredToken() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        String token = sign(jwtProperties.rsaPublicKey(), jwtProperties.rsaPrivateKey(),
            issuedAt, issuedAt.plus(Duration.ofMinutes(30)));

        expectUnauthorized(token);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰이면 401 ProblemDetail로 응답한다")
    void otherKeyToken() {
        KeyPair otherKeyPair = generateKeyPair();
        Instant now = Instant.now();
        String token = sign((RSAPublicKey) otherKeyPair.getPublic(), (RSAPrivateKey) otherKeyPair.getPrivate(),
            now, now.plus(Duration.ofMinutes(30)));

        expectUnauthorized(token);
    }

    @Test
    @DisplayName("형식이 잘못된 토큰이면 401 ProblemDetail로 응답한다")
    void malformedToken() {
        expectUnauthorized("not-a-jwt");
    }

    @Test
    @DisplayName("BUYER는 판매자 API를 호출할 수 없다")
    void buyerCannotCallSellerApi() {
        expectForbidden(SELLER_PATH, accessTokenIssuer.issue(1L, List.of("BUYER")));
    }

    @Test
    @DisplayName("BUYER는 관리자 API를 호출할 수 없다")
    void buyerCannotCallAdminApi() {
        expectForbidden(ADMIN_PATH, accessTokenIssuer.issue(1L, List.of("BUYER")));
    }

    @Test
    @DisplayName("SELLER는 판매자 API를 호출할 수 있다")
    void sellerCanCallSellerApi() {
        expectOk(SELLER_PATH, accessTokenIssuer.issue(2L, List.of("BUYER", "SELLER")));
    }

    @Test
    @DisplayName("ADMIN은 관리자 API를 호출할 수 있다")
    void adminCanCallAdminApi() {
        expectOk(ADMIN_PATH, accessTokenIssuer.issue(3L, List.of("BUYER", "ADMIN")));
    }

    private void expectOk(String path, String token) {
        client.get().uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isOk();
    }

    private void expectUnauthorized(String token) {
        client.get().uri(AUTH_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 필요합니다")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo(AUTH_PATH);
    }

    private void expectForbidden(String path, String token) {
        client.get().uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("권한이 없습니다")
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.instance").isEqualTo(path);
    }

    private static String sign(RSAPublicKey publicKey, RSAPrivateKey privateKey, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("1")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", List.of("BUYER"))
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return NimbusJwtEncoder.withKeyPair(publicKey, privateKey).build()
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();
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
