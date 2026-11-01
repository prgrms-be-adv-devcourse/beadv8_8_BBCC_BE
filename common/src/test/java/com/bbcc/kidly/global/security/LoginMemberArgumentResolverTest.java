package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class LoginMemberArgumentResolverTest {

    private final LoginMemberArgumentResolver resolver = new LoginMemberArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    @DisplayName("@LoginMember가 붙은 파라미터만 맡는다")
    void supportsOnlyLoginMember() throws NoSuchMethodException {
        var method = Controller.class.getDeclaredMethod("handle", AuthMember.class, AuthMember.class);

        assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
        assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
    }

    @Test
    @DisplayName("회원 헤더의 회원 ID · 닉네임(퍼센트 디코드)으로 로그인 회원을 만든다")
    void resolvesMember() {
        request.addHeader(MemberHeaders.MEMBER_ID, "7");
        request.addHeader(MemberHeaders.MEMBER_NICKNAME, MemberHeaders.encodeNickname("소소 맘+"));

        assertThat(resolve()).isEqualTo(new AuthMember(7L, "소소 맘+"));
    }

    @Test
    @DisplayName("닉네임 헤더가 없으면 닉네임은 null이다")
    void noNickname() {
        request.addHeader(MemberHeaders.MEMBER_ID, "7");

        assertThat(resolve()).isEqualTo(new AuthMember(7L, null));
    }

    @Test
    @DisplayName("역할 헤더는 보지 않는다(역할은 Gateway가 검사한다)")
    void ignoresRoles() {
        request.addHeader(MemberHeaders.MEMBER_ID, "7");
        request.addHeader(MemberHeaders.MEMBER_ROLES, "OWNER");

        assertThat(resolve()).isEqualTo(new AuthMember(7L, null));
    }

    @Test
    @DisplayName("회원 ID 헤더가 없으면 401이다")
    void missingMemberId() {
        assertUnauthorized();
    }

    @ParameterizedTest
    @CsvSource({"abc, 회원", "-7, 회원", "7, %ZZ"})
    @DisplayName("회원 ID · 닉네임 헤더 값이 잘못되면 401이다")
    void invalidHeader(String memberId, String nickname) {
        request.addHeader(MemberHeaders.MEMBER_ID, memberId);
        request.addHeader(MemberHeaders.MEMBER_NICKNAME, nickname);

        assertUnauthorized();
    }

    private AuthMember resolve() {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(this::resolve)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    private static class Controller {

        @SuppressWarnings("unused")
        void handle(@LoginMember AuthMember member, AuthMember other) {
        }
    }
}
