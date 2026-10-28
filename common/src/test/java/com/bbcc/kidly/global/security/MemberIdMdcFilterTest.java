package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.filter.RequestLoggingFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MemberIdMdcFilterTest {

    private final MemberIdMdcFilter filter = new MemberIdMdcFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("토큰으로 인증된 요청이면 회원 ID를 MDC에 넣어 요청 중 로그에 붙인다")
    void putsMemberIdOfAuthenticatedRequest() throws Exception {
        var jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("7").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(memberIdDuringRequest()).isEqualTo("7");
    }

    @Test
    @DisplayName("토큰으로 인증되지 않은 요청은 회원 ID를 넣지 않는다")
    void skipsUnauthenticatedRequest() throws Exception {
        assertThat(memberIdDuringRequest()).isNull();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("anonymous", null));
        assertThat(memberIdDuringRequest()).isNull();
    }

    private String memberIdDuringRequest() throws Exception {
        var memberId = new AtomicReference<String>();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
            (request, response) -> memberId.set(MDC.get(RequestLoggingFilter.MEMBER_ID_MDC_KEY)));
        return memberId.get();
    }
}
