package com.bbcc.kidly.global.security;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 로그인 회원을 만드는 방식. jwt(기본): Access Token을 직접 검증, gateway: API Gateway가 붙인 X-Member-* 헤더를 믿는다
    // 값은 AuthProperties.Mode와 같아야 한다(모르는 값이면 AuthProperties 바인딩에서 기동 실패)
    static final String AUTH_MODE = "kidly.auth.mode";
    static final String JWT_MODE = "jwt";
    static final String GATEWAY_MODE = "gateway";

    private static final String[] PUBLIC_PATHS = {
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/error",
        "/actuator/health",
        "/actuator/prometheus"
    };

    // jwt 모드: 서비스가 Access Token을 직접 검증한다(prod · 로컬 · 테스트, Gateway가 없는 곳)
    @Bean
    @ConditionalOnProperty(name = AUTH_MODE, havingValue = JWT_MODE, matchIfMissing = true)
    public SecurityFilterChain jwtSecurityFilterChain(
        HttpSecurity http,
        SecurityProblemHandler problemHandler,
        PublicApiRequestMatcher publicApis) throws Exception {
        return commonSecurity(http, problemHandler, publicApis)
            .cors(Customizer.withDefaults())
            // 보낸 토큰은 공개 API에서도 검증한다. 잘못된 토큰(여기)과 토큰 없음(exceptionHandling) 모두 ProblemDetail로 응답한다
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new AuthMemberJwtAuthenticationConverter()))
                .authenticationEntryPoint(problemHandler))
            .addFilterAfter(new MemberIdMdcFilter(), BearerTokenAuthenticationFilter.class)
            .build();
    }

    // gateway 모드: Gateway가 검증하고 붙인 X-Member-* 헤더로 로그인 회원을 만든다. 토큰을 다시 검증하지 않고, CORS는 Gateway가 맡는다
    @Bean
    @ConditionalOnProperty(name = AUTH_MODE, havingValue = GATEWAY_MODE)
    public SecurityFilterChain gatewaySecurityFilterChain(
        HttpSecurity http,
        SecurityProblemHandler problemHandler,
        PublicApiRequestMatcher publicApis) throws Exception {
        return commonSecurity(http, problemHandler, publicApis)
            .cors(AbstractHttpConfigurer::disable)
            .addFilterAt(new GatewayHeaderAuthenticationFilter(problemHandler),
                AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterAfter(new MemberIdMdcFilter(), GatewayHeaderAuthenticationFilter.class)
            .build();
    }

    // 두 모드가 같이 쓰는 것: 권한 규칙 · 401/403 ProblemDetail
    private static HttpSecurity commonSecurity(
        HttpSecurity http,
        SecurityProblemHandler problemHandler,
        PublicApiRequestMatcher publicApis) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(publicApis).permitAll()
                .requestMatchers("/api/v1/seller/**").hasRole(Role.SELLER.name())
                .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                .anyRequest().authenticated())
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(problemHandler)
                .accessDeniedHandler(problemHandler));
    }

    // 발급은 회원 도메인(개인키), 검증은 여기서 공개키로만 한다.
    // 기본 검사(typ · 만료 등, 시계 오차는 CLOCK_SKEW) 뒤 무효화된 회원인지 본다
    @Bean
    @ConditionalOnProperty(name = AUTH_MODE, havingValue = JWT_MODE, matchIfMissing = true)
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties, AccessTokenRevocations revocations) {
        var decoder = NimbusJwtDecoder.withPublicKey(jwtProperties.rsaPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
            new JwtTimestampValidator(JwtProperties.CLOCK_SKEW), new RevokedAccessTokenValidator(revocations)));
        return decoder;
    }

    // 프론트 origin만 허용한다. 리프레시 토큰 쿠키를 고려해 credentials를 켜 둔다. gateway 모드는 Gateway가 CORS를 맡는다
    @Bean
    @ConditionalOnProperty(name = AUTH_MODE, havingValue = JWT_MODE, matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 프론트가 오류 문의에 traceId를 붙일 수 있게 응답 헤더를 JS에 보여 준다
        config.setExposedHeaders(List.of(RequestLoggingFilter.TRACE_ID_HEADER));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
