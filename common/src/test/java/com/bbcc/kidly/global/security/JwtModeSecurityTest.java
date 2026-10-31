package com.bbcc.kidly.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.global.config.PropertiesConfig;
import jakarta.servlet.Filter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * jwt 모드의 Security 필터 체인. 테스트에서 만든 키로 서명한 토큰을 보낸다.
 * 앱처럼 보안 패키지를 스캔한다(테스트 클래스 안의 빈은 뺀다). DB를 쓰지 않으므로 DataSource · JPA 자동 설정을 뺀다.
 */
@SpringBootTest(classes = JwtModeSecurityTest.TestApp.class)
class JwtModeSecurityTest {

    private static final String LOGIN_MEMBER_PATH = "/api/v1/test/login-member";
    private static final KeyPair KEYS = generateKeys();
    private static final JwtEncoder ENCODER = NimbusJwtEncoder
        .withKeyPair((RSAPublicKey) KEYS.getPublic(), (RSAPrivateKey) KEYS.getPrivate())
        .build();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @DynamicPropertySource
    static void publicKey(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", JwtModeSecurityTest::writePublicKey);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    @Test
    @DisplayName("검증된 토큰으로 로그인 회원을 받는다")
    void resolvesLoginMemberFromToken() throws Exception {
        mvc.perform(get(LOGIN_MEMBER_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("BUYER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.roles[0]").value("BUYER"));
    }

    @Test
    @DisplayName("서명은 맞지만 모르는 역할이 든 토큰은 401 ProblemDetail이다")
    void rejectsUnknownRole() throws Exception {
        mvc.perform(get(LOGIN_MEMBER_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("OWNER")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401));
    }

    private static String token(String role) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject("7")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .claim("roles", List.of(role))
            .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static KeyPair generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // JwtProperties는 PEM 파일 위치를 받는다
    private static String writePublicKey() {
        try {
            var file = Files.createTempFile("jwt-public", ".pem");
            file.toFile().deleteOnExit();
            Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");
            return file.toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
    @ComponentScan(basePackageClasses = SecurityConfig.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test\\$.*"))
    @Import({PropertiesConfig.class, TestController.class})
    static class TestApp {

        // 무효화 확인은 회원 도메인 구현이 맡는다. 여기서는 아무것도 무효화하지 않는다
        @Bean
        AccessTokenRevocations accessTokenRevocations() {
            return (memberId, issuedAt) -> false;
        }
    }

    @RestController
    static class TestController {

        @GetMapping(LOGIN_MEMBER_PATH)
        AuthMember loginMember(@LoginMember AuthMember member) {
            return member;
        }
    }
}
