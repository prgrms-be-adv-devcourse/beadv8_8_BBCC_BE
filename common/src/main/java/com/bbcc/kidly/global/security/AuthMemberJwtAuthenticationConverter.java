package com.bbcc.kidly.global.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * jwt 모드에서 검증된 Access Token을 인증으로 바꾼다. principal은 {@link AuthMember}(gateway 모드와 같다), credentials는 Jwt다.
 * 권한은 Spring의 JwtAuthenticationConverter가 만든 그대로 쓴다: roles 클레임(BUYER · SELLER · ADMIN)을 ROLE_ 권한으로.
 * 서명은 맞아도 모르는 역할 · 숫자가 아닌 회원 ID면 잘못된 토큰으로 본다(진입점이 401).
 */
class AuthMemberJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = jwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var authorities = delegate.convert(jwt).getAuthorities();
        return new JwtAuthenticationToken(jwt, member(jwt), authorities);
    }

    // 그대로 던지면 서블릿 밖으로 나가 500이 된다. 토큰 값은 메시지에 넣지 않는다
    private static AuthMember member(Jwt jwt) {
        try {
            return AuthMember.from(jwt);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Access Token의 회원 정보가 잘못됐습니다");
        }
    }

    // 클레임 이름은 JwtAccessTokenIssuer와 같아야 한다
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
