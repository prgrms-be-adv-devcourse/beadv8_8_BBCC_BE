package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

class GatewayHeaderAuthenticationFilterTest {

    private final GatewayHeaderAuthenticationFilter filter =
        new GatewayHeaderAuthenticationFilter(new SecurityProblemHandler());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("회원 ID 헤더가 없으면 인증하지 않고 넘긴다(공개 API는 통과, 나머지는 진입점이 401)")
    void skipsWithoutMemberId() throws Exception {
        var result = authenticationDuringRequest();

        assertThat(result.passed()).isTrue();
        assertThat(result.authentication()).isNull();
    }

    @Test
    @DisplayName("헤더의 회원 ID · 역할 · 닉네임으로 로그인 회원을 만들고, 역할은 ROLE_ 권한이 된다")
    void authenticatesWithHeaders() throws Exception {
        var nickname = "키들 맘+100%";
        request.addHeader("X-Member-Id", "7");
        request.addHeader("X-Member-Roles", "BUYER,SELLER");
        // Gateway와 같은 규칙: UTF-8 퍼센트 인코딩, 공백은 %20
        request.addHeader("X-Member-Nickname",
            URLEncoder.encode(nickname, StandardCharsets.UTF_8).replace("+", "%20"));

        var result = authenticationDuringRequest();

        assertThat(result.passed()).isTrue();
        var authentication = result.authentication();
        assertThat(authentication.getPrincipal())
            .isEqualTo(new AuthMember(7L, nickname, List.of(Role.BUYER, Role.SELLER)));
        assertThat(authentication.getName()).isEqualTo("7");
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_BUYER", "ROLE_SELLER");
    }

    @Test
    @DisplayName("인증을 요청 속성에도 저장해 ERROR · ASYNC 재진입 때도 로그인 회원이 남는다(jwt 모드와 같다)")
    void savesContextToRequest() throws Exception {
        request.addHeader("X-Member-Id", "7");
        request.addHeader("X-Member-Roles", "BUYER");

        var authentication = authenticationDuringRequest().authentication();

        var saved = new RequestAttributeSecurityContextRepository().loadDeferredContext(request).get();
        assertThat(saved.getAuthentication()).isSameAs(authentication);
    }

    @Test
    @DisplayName("닉네임 헤더가 없으면 닉네임은 null, 역할이 비어 있으면 역할이 없다")
    void nicknameNullAndNoRoles() throws Exception {
        request.addHeader("X-Member-Id", "7");
        request.addHeader("X-Member-Roles", "");

        var authentication = authenticationDuringRequest().authentication();

        assertThat(authentication.getPrincipal()).isEqualTo(new AuthMember(7L, null, List.of()));
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
        "X-Member-Id, abc",
        "X-Member-Roles, OWNER",
        "X-Member-Nickname, %zz"
    })
    @DisplayName("헤더 값이 잘못되면 인증하지 않고 401 ProblemDetail로 끝낸다")
    void rejectsInvalidHeaders(String header, String value) throws Exception {
        request.addHeader("X-Member-Id", "7");
        request.addHeader("X-Member-Roles", "BUYER");
        request.addHeader("X-Member-Nickname", "nick");
        request.removeHeader(header);
        request.addHeader(header, value);

        var result = authenticationDuringRequest();

        assertThat(result.passed()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":401").doesNotContain(value);
    }

    private Result authenticationDuringRequest() throws Exception {
        var authentication = new AtomicReference<Authentication>();
        var passed = new AtomicReference<>(false);
        filter.doFilter(request, response, (req, res) -> {
            passed.set(true);
            authentication.set(SecurityContextHolder.getContext().getAuthentication());
        });
        return new Result(passed.get(), authentication.get());
    }

    private record Result(boolean passed, Authentication authentication) {
    }
}
