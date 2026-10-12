package com.bbcc.kidly.global.config;

import com.bbcc.kidly.global.auth.PublicApiRequestMatcher;
import com.bbcc.kidly.global.error.SecurityProblemHandler;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SecurityProblemHandler problemHandler,
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) throws Exception {
        // 고정 공개 경로(Swagger, /error)와 각 도메인이 @PublicApi로 선언한 API
        RequestMatcher publicRequests = new OrRequestMatcher(
            new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS)
                .map(PathPatternRequestMatcher::pathPattern)
                .map(RequestMatcher.class::cast)
                .toList()),
            new PublicApiRequestMatcher(handlerMapping));

        return http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(publicRequests).permitAll()
                .requestMatchers("/api/v1/seller/**").hasRole("SELLER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            // 잘못된 토큰은 BearerTokenAuthenticationFilter가, 토큰 없음은 ExceptionTranslationFilter가 처리한다.
            // 두 곳 모두 ProblemDetail로 응답하도록 entry point를 각각 지정한다
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver(publicRequests))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(problemHandler))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(problemHandler)
                .accessDeniedHandler(problemHandler))
            .build();
    }

    // 공개 요청은 토큰을 꺼내지 않아 검증하지 않는다. 잘못된 토큰도, 유효한 토큰도 무시하고 비로그인으로 처리한다
    private BearerTokenResolver bearerTokenResolver(RequestMatcher publicRequests) {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> publicRequests.matches(request) ? null : delegate.resolve(request);
    }

    // 발급은 회원 도메인(개인키), 검증은 여기서 공개키로만 한다
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        return NimbusJwtDecoder.withPublicKey(jwtProperties.rsaPublicKey()).build();
    }

    // roles 클레임(BUYER · SELLER · ADMIN)을 ROLE_ 권한으로 바꾼다. 클레임 이름은 AccessTokenIssuer와 같아야 한다
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    // 프론트 origin만 허용한다. 리프레시 토큰 쿠키를 고려해 credentials를 켜 둔다
    // TODO(인프라): 운영 URL 확정되면 application-prod.yml에 cors.allowed-origins 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
