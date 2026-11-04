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

class LoginAdminArgumentResolverTest {
    private final LoginAdminArgumentResolver resolver = new LoginAdminArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void resolvesAdmin() {
        request.addHeader(PrincipalHeaders.ADMIN_ID, "7");
        assertThat(resolve()).isEqualTo(new AuthAdmin(7L));
    }

    @Test
    void missingHeaderIsUnauthorized() {
        assertUnauthorized();
    }

    @Test
    void memberHeaderDoesNotAuthenticateAdmin() {
        request.addHeader(MemberHeaders.MEMBER_ID, "7");
        assertUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidId(String id) {
        request.addHeader(PrincipalHeaders.ADMIN_ID, id);
        assertUnauthorized();
    }

    private AuthAdmin resolve() {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private void assertUnauthorized() {
        assertThatThrownBy(this::resolve).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
