package com.bbcc.kidly.member.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.member.support.security.AuthChild;
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
    void rejectsMissingHeaders() {
        assertUnauthorized();
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, "7");
        assertUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidHeaders(String value) {
        request.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, value);
        request.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, "8");
        assertUnauthorized();
        var other = new MockHttpServletRequest();
        other.addHeader(PrincipalHeaders.CHILD_ACCOUNT_ID, "7");
        other.addHeader(PrincipalHeaders.CHILD_GUARDIAN_ID, value);
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(other), null))
            .isInstanceOf(BusinessException.class);
    }

    private AuthChild resolve() {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(this::resolve).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
