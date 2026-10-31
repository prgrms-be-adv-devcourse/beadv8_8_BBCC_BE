package com.bbcc.kidly.global.security;

import static com.bbcc.kidly.support.TestApiController.ADMIN_PATH;
import static com.bbcc.kidly.support.TestApiController.AUTH_PATH;
import static com.bbcc.kidly.support.TestApiController.SELLER_PATH;

import com.bbcc.kidly.support.FunctionalTest;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Disabled;
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
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("로그인 회원이면 로그인이 필요한 API를 호출할 수 있다")
    void validToken() {
        expectOk(AUTH_PATH, TestMembers.buyer(1L));
    }

    @Test
    @Disabled("gateway 모드: 토큰 검증은 Gateway가 한다. #638 Task 3에서 지운다")
    @DisplayName("만료된 토큰이면 401 ProblemDetail로 응답한다")
    void expiredToken() {
        expectUnauthorized(sign(serverKeyPair(), Instant.now().minus(Duration.ofMinutes(30))));
    }

    @Test
    @Disabled("gateway 모드: 토큰 검증은 Gateway가 한다. #638 Task 3에서 지운다")
    @DisplayName("다른 키로 서명한 토큰이면 401 ProblemDetail로 응답한다")
    void otherKeyToken() {
        expectUnauthorized(sign(otherKeyPair(), Instant.now().plus(Duration.ofMinutes(30))));
    }

    @Test
    @Disabled("gateway 모드: 토큰 검증은 Gateway가 한다. #638 Task 3에서 지운다")
    @DisplayName("형식이 잘못된 토큰이면 401 ProblemDetail로 응답한다")
    void malformedToken() {
        expectUnauthorized("not-a-jwt");
    }

    @Test
    @Disabled("gateway 모드: 토큰 검증은 Gateway가 한다. #638 Task 3에서 지운다")
    @DisplayName("서명하지 않은 토큰(alg: none)이면 401 ProblemDetail로 응답한다")
    void unsignedToken() {
        expectUnauthorized(unsignedAdminToken());
    }

    @Test
    @DisplayName("BUYER는 판매자 API를 호출할 수 없다")
    void buyerCannotCallSellerApi() {
        expectForbidden(SELLER_PATH, TestMembers.buyer(1L));
    }

    @Test
    @DisplayName("BUYER는 관리자 API를 호출할 수 없다")
    void buyerCannotCallAdminApi() {
        expectForbidden(ADMIN_PATH, TestMembers.buyer(1L));
    }

    @Test
    @DisplayName("SELLER는 관리자 API를 호출할 수 없다")
    void sellerCannotCallAdminApi() {
        expectForbidden(ADMIN_PATH, TestMembers.seller(1L));
    }

    @Test
    @DisplayName("SELLER는 판매자 API를 호출할 수 있다")
    void sellerCanCallSellerApi() {
        expectOk(SELLER_PATH, TestMembers.seller(1L));
    }

    @Test
    @DisplayName("ADMIN은 관리자 API를 호출할 수 있다")
    void adminCanCallAdminApi() {
        expectOk(ADMIN_PATH, TestMembers.admin(1L));
    }

    private void expectOk(String path, Consumer<HttpHeaders> login) {
        client.get().uri(path)
            .headers(login)
            .exchange()
            .expectStatus().isOk();
    }

    private void expectUnauthorized(String token) {
        client.get().uri(AUTH_PATH)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("로그인이 필요합니다")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.instance").isEqualTo(AUTH_PATH);
    }

    private void expectForbidden(String path, Consumer<HttpHeaders> login) {
        client.get().uri(path)
            .headers(login)
            .exchange()
            .expectStatus().isForbidden()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("권한이 없습니다")
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.instance").isEqualTo(path);
    }

    private KeyPair serverKeyPair() {
        return new KeyPair(jwtProperties.rsaPublicKey(), jwtProperties.rsaPrivateKey());
    }

    private static KeyPair otherKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(KeyPair keyPair, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("1")
            .issuedAt(expiresAt.minus(Duration.ofMinutes(30)))
            .expiresAt(expiresAt)
            .claim("roles", List.of("BUYER"))
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
            .build()
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();
    }

    // 서명 부분이 비어 있는 토큰. 검증하지 않으면 누구나 ADMIN이 될 수 있다
    private static String unsignedAdminToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        long exp = Instant.now().plus(Duration.ofMinutes(30)).getEpochSecond();
        String header = "{\"alg\":\"none\"}";
        String payload = "{\"sub\":\"1\",\"roles\":[\"ADMIN\"],\"exp\":" + exp + "}";
        return encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".";
    }
}
