package com.bbcc.kidly.global.security;

import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.Role;
import java.util.Arrays;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;

/**
 * 기능 테스트에서 로그인한 회원을 Gateway가 붙이는 X-Member-* 헤더로 보낸다. 예: {@code .headers(TestMembers.buyer(1L))}
 * 서비스는 토큰을 보지 않고 이 헤더만으로 로그인 회원을 만든다(LoginMemberArgumentResolver).
 */
public final class TestMembers {

    private TestMembers() {
    }

    public static String nickname(Long memberId) {
        return "회원" + memberId;
    }

    // 로그인하지 않은 요청: 회원 헤더를 붙이지 않는다
    public static Consumer<HttpHeaders> anonymous() {
        return headers -> {
        };
    }

    public static Consumer<HttpHeaders> buyer(Long memberId) {
        return of(memberId, nickname(memberId), Role.BUYER);
    }

    public static Consumer<HttpHeaders> seller(Long memberId) {
        return of(memberId, nickname(memberId), Role.BUYER, Role.SELLER);
    }

    public static Consumer<HttpHeaders> admin(Long memberId) {
        return of(memberId, nickname(memberId), Role.BUYER, Role.ADMIN);
    }

    // 닉네임이 null이면(nickname 클레임이 없는 토큰) Gateway처럼 닉네임 헤더를 붙이지 않는다
    public static Consumer<HttpHeaders> of(Long memberId, String nickname, Role... roles) {
        var roleNames = MemberHeaders.encodeRoles(Arrays.stream(roles).map(Role::name).toList());
        return headers -> {
            headers.set(MemberHeaders.MEMBER_ID, String.valueOf(memberId));
            headers.set(MemberHeaders.MEMBER_ROLES, roleNames);
            if (nickname != null) {
                headers.set(MemberHeaders.MEMBER_NICKNAME, MemberHeaders.encodeNickname(nickname));
            }
        };
    }
}
