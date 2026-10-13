package com.bbcc.kidly.global.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Access Token에서 꺼낸 로그인 회원 정보. 컨트롤러에서 {@code @LoginMember AuthMember member}로 받는다.
 *
 * @param nickname 토큰을 발급할 때의 닉네임. nickname 클레임이 없는 토큰(배포 전 발급)이면 null
 */
public record AuthMember(Long id, String nickname, List<Role> roles) {

    // 클레임 이름은 JwtAccessTokenIssuer와 같아야 한다
    private static final String NICKNAME_CLAIM = "nickname";
    private static final String ROLES_CLAIM = "roles";

    public static AuthMember from(Jwt jwt) {
        var roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        return new AuthMember(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(NICKNAME_CLAIM),
            roles == null ? List.of() : roles.stream().map(Role::valueOf).toList());
    }
}
