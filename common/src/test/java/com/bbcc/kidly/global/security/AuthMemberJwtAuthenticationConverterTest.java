package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.shared.auth.Role;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class AuthMemberJwtAuthenticationConverterTest {

    private final AuthMemberJwtAuthenticationConverter converter = new AuthMemberJwtAuthenticationConverter();

    @Test
    @DisplayName("토큰의 회원 ID · 닉네임 · 역할로 AuthMember principal을 만들고, 역할은 ROLE_ 권한이 된다")
    void convertsToAuthMember() {
        var jwt = jwt().claim("nickname", "회원7").claim("roles", List.of("BUYER", "SELLER")).build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getPrincipal())
            .isEqualTo(new AuthMember(7L, "회원7", List.of(Role.BUYER, Role.SELLER)));
        assertThat(authentication.getCredentials()).isEqualTo(jwt);
        assertThat(authentication.getName()).isEqualTo("7");
        assertThat(authentication.isAuthenticated()).isTrue();
        // 권한은 바꾸기 전(JwtAuthenticationConverter)과 같다: 역할 권한 + Spring Security의 Bearer 인증 요소
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_BUYER", "ROLE_SELLER", FactorGrantedAuthority.BEARER_AUTHORITY);
    }

    @Test
    @DisplayName("nickname 클레임이 없는 토큰이면 닉네임은 null이다")
    void nicknameIsNullWithoutClaim() {
        var jwt = jwt().claim("roles", List.of("BUYER")).build();

        var authentication = converter.convert(jwt);

        assertThat(authentication.getPrincipal()).isEqualTo(new AuthMember(7L, null, List.of(Role.BUYER)));
    }

    // 서명이 맞아도 회원이 만들지 않는 값이면 잘못된 토큰(401)이다. 값은 메시지에 넣지 않는다
    @ParameterizedTest(name = "sub={0}, roles={1}")
    @CsvSource({
        "7, OWNER",
        "abc, BUYER"
    })
    @DisplayName("모르는 역할이나 숫자가 아닌 회원 ID가 든 토큰은 InvalidBearerTokenException이다")
    void rejectsUnknownValues(String subject, String role) {
        var jwt = jwt().subject(subject).claim("roles", List.of(role)).build();

        assertThatThrownBy(() -> converter.convert(jwt))
            .isInstanceOf(InvalidBearerTokenException.class)
            .message().doesNotContain(subject).doesNotContain(role);
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("7");
    }
}
