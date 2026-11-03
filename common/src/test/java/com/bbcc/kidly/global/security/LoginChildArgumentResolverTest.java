package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class LoginChildArgumentResolverTest {
    private final LoginChildArgumentResolver resolver = new LoginChildArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void resolvesChild() {
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, "7");
        request.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, "8");
        assertThat(resolve()).isEqualTo(new AuthChild(7L, 8L));
    }

    @Test
    void rejectsMissingAndMemberHeaders() {
        assertUnauthorized();
        request.addHeader(MemberHeaders.MEMBER_ID, "7");
        assertUnauthorized();
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, "7");
        assertUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidIds(String id) {
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, id);
        request.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, "8");
        assertUnauthorized();
        request.removeHeader(PrincipalHeaders.CHILD_ACCOUNT_ID);
        request.removeHeader(PrincipalHeaders.CHILD_GUARDIAN_ID);
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, "7");
        request.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, id);
        assertUnauthorized();
    }

    private AuthChild resolve() {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(this::resolve).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
