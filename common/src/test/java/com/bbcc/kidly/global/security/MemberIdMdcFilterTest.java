package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import com.bbcc.kidly.shared.auth.Role;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class MemberIdMdcFilterTest {

    private final MemberIdMdcFilter filter = new MemberIdMdcFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("로그인 회원으로 인증된 요청이면 회원 ID를 MDC에 넣어 요청 중 로그에 붙인다")
    void putsMemberIdOfAuthenticatedRequest() throws Exception {
        var member = new AuthMember(7L, "회원7", List.of(Role.BUYER));
        SecurityContextHolder.getContext()
            .setAuthentication(new TestingAuthenticationToken(member, null, "ROLE_BUYER"));

        assertThat(memberIdDuringRequest()).isEqualTo("7");
    }

    @Test
    @DisplayName("인증되지 않았거나 익명인 요청은 회원 ID를 넣지 않는다")
    void skipsUnauthenticatedRequest() throws Exception {
        assertThat(memberIdDuringRequest()).isNull();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(memberIdDuringRequest()).isNull();
    }

    private String memberIdDuringRequest() throws Exception {
        var memberId = new AtomicReference<String>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
            (request, response) -> memberId.set(MDC.get(RequestLoggingFilter.MEMBER_ID_MDC_KEY)));
        return memberId.get();
    }
}
