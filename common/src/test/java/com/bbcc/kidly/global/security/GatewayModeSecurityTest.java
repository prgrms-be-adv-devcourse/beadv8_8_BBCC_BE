package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.global.config.PropertiesConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * gateway 모드의 Security 필터 체인. 앱(kidly-app)의 통합 · 기능 테스트는 jwt 모드라 여기서 보안 설정만 따로 띄운다.
 * 앱처럼 보안 패키지를 스캔한다(테스트 클래스 안의 빈은 뺀다). 회원 DB가 없는 서비스처럼 AccessTokenRevocations 빈을 두지 않는다.
 * DB를 쓰지 않으므로 DataSource · JPA 자동 설정을 뺀다.
 */
@SpringBootTest(classes = GatewayModeSecurityTest.TestApp.class, properties = {
    "kidly.auth.mode=gateway",
    "cors.allowed-origins=http://front.test"})
class GatewayModeSecurityTest {

    private static final String PUBLIC_PATH = "/api/v1/test/public";
    private static final String LOGIN_MEMBER_PATH = "/api/v1/test/login-member";
    private static final String SELLER_PATH = "/api/v1/seller/test";
    private static final String ADMIN_PATH = "/api/v1/admin/test";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    @Test
    @DisplayName("Gateway가 붙인 헤더로 로그인 회원을 받는다")
    void resolvesLoginMemberFromHeaders() throws Exception {
        mvc.perform(get(LOGIN_MEMBER_PATH)
                .header("X-Member-Id", "7")
                .header("X-Member-Roles", "BUYER,SELLER")
                .header("X-Member-Nickname", "%ED%82%A4%EB%93%A4%20%EB%A7%98"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.nickname").value("키들 맘"))
            .andExpect(jsonPath("$.roles[0]").value("BUYER"))
            .andExpect(jsonPath("$.roles[1]").value("SELLER"))
            .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    @DisplayName("헤더가 없으면 공개 API는 통과하고, 로그인이 필요한 API는 401 ProblemDetail이다")
    void anonymousRequest() throws Exception {
        mvc.perform(get(PUBLIC_PATH)).andExpect(status().isOk());

        mvc.perform(get(LOGIN_MEMBER_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("판매자 API는 SELLER 역할, 관리자 API는 ADMIN 역할이 있어야 한다")
    void checksRoles() throws Exception {
        mvc.perform(get(SELLER_PATH).header("X-Member-Id", "7").header("X-Member-Roles", "BUYER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get(SELLER_PATH).header("X-Member-Id", "7").header("X-Member-Roles", "BUYER,SELLER"))
            .andExpect(status().isOk());

        mvc.perform(get(ADMIN_PATH).header("X-Member-Id", "7").header("X-Member-Roles", "BUYER,SELLER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

        mvc.perform(get(ADMIN_PATH).header("X-Member-Id", "1").header("X-Member-Roles", "BUYER,ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bearer 토큰은 보지 않는다: 토큰만 보내면 로그인이 아니고, 잘못된 토큰이 있어도 회원 헤더로 로그인한다")
    void ignoresBearerToken() throws Exception {
        mvc.perform(get(LOGIN_MEMBER_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
            .andExpect(status().isUnauthorized());

        mvc.perform(get(LOGIN_MEMBER_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
                .header("X-Member-Id", "7")
                .header("X-Member-Roles", "BUYER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));

        mvc.perform(get(PUBLIC_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS는 Gateway가 맡으므로 허용 origin이 설정돼 있어도 CORS 헤더를 붙이지 않는다")
    void noCors() throws Exception {
        mvc.perform(get(PUBLIC_PATH).header(HttpHeaders.ORIGIN, "http://front.test"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        assertThat(context.containsBean("corsConfigurationSource")).isFalse();
    }

    @Test
    @DisplayName("JWT 검증기를 만들지 않는다(공개키 · AccessTokenRevocations 빈 없이도 뜬다)")
    void noJwtDecoder() {
        assertThat(context.getBeanNamesForType(JwtDecoder.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AccessTokenRevocations.class)).isEmpty();
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
    }

    @RestController
    static class TestController {

        @PublicApi
        @GetMapping(PUBLIC_PATH)
        void publicApi() {
        }

        @GetMapping(LOGIN_MEMBER_PATH)
        AuthMember loginMember(@LoginMember AuthMember member) {
            return member;
        }

        @GetMapping(SELLER_PATH)
        void seller() {
        }

        @GetMapping(ADMIN_PATH)
        void admin() {
        }
    }
}
