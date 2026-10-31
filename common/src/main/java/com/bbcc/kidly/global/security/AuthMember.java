package com.bbcc.kidly.global.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 로그인 회원 정보. 인증의 principal이고, 컨트롤러에서 {@code @LoginMember AuthMember member}로 받는다.
 * {@link #getName()}(Authentication.getName())은 회원 ID다. 로그로 샐 수 있어 {@link #toString()}에 닉네임을 넣지 않는다.
 *
 * @param nickname 토큰을 발급할 때의 닉네임. nickname 클레임이 없는 토큰(배포 전 발급)이면 null
 */
public record AuthMember(Long id, String nickname, List<Role> roles) implements AuthenticatedPrincipal {

    // 클레임 이름은 JwtAccessTokenIssuer와 같아야 한다
    private static final String NICKNAME_CLAIM = "nickname";
    private static final String ROLES_CLAIM = "roles";

    public static AuthMember from(Jwt jwt) {
        var roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        return new AuthMember(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(NICKNAME_CLAIM),
            roles == null ? List.of() : roles.stream().map(Role::valueOf).toList());
    }

    // 응답 JSON에 name 칸으로 나가지 않게 한다(필드는 id · nickname · roles 그대로)
    @JsonIgnore
    @Override
    public String getName() {
        return String.valueOf(id);
    }

    @Override
    public String toString() {
        return "AuthMember[id=" + id + ", roles=" + roles + "]";
    }
}
