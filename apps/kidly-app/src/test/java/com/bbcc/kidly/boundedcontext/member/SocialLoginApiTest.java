package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import com.bbcc.kidly.boundedcontext.member.out.JpaMemberRepository;
import com.bbcc.kidly.support.FakeOAuthClient;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SocialLoginApiTest {

    private static final String KAKAO_LOGIN_PATH = "/api/v1/auth/login/kakao";

    @Autowired
    private RestTestClient client;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JpaMemberRepository jpaMemberRepository;

    @Test
    @DisplayName("처음 로그인하면 가입되고 그 회원의 Access Token을 받는다")
    void firstLogin() {
        var memberId = memberIdOf(login("kakao-1"));

        assertThat(memberRepository.findById(memberId)).isPresent();
    }

    @Test
    @DisplayName("이미 가입한 회원은 같은 회원으로 로그인한다")
    void existingMember() {
        var saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThat(memberIdOf(login("kakao-1"))).isEqualTo(saved.getId());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("정지된 회원이면 403")
    void suspendedMember() {
        memberRepository.save(MemberFixture.withStatus(MemberStatus.SUSPENDED));

        expectProblem(KAKAO_LOGIN_PATH, codeBody("kakao-1"), HttpStatus.FORBIDDEN, "로그인할 수 없는 회원입니다");
    }

    @Test
    @DisplayName("잘못된 인가 코드면 400")
    void invalidCode() {
        expectProblem(KAKAO_LOGIN_PATH, codeBody(FakeOAuthClient.INVALID_CODE),
            HttpStatus.BAD_REQUEST, "인가 코드가 올바르지 않습니다");
    }

    @Test
    @DisplayName("처음 로그인인데 제공자 이메일이 없으면 400")
    void firstLoginWithoutEmail() {
        expectProblem(KAKAO_LOGIN_PATH, codeBody(FakeOAuthClient.NO_EMAIL_CODE),
            HttpStatus.BAD_REQUEST, "이메일 제공이 필요합니다");
    }

    @Test
    @DisplayName("인가 코드가 비어 있으면 400")
    void blankCode() {
        expectProblem(KAKAO_LOGIN_PATH, codeBody(" "), HttpStatus.BAD_REQUEST, "잘못된 요청입니다");
    }

    @Test
    @DisplayName("지원하지 않는 제공자면 400")
    void unsupportedProvider() {
        expectProblem("/api/v1/auth/login/unknown", codeBody("kakao-1"),
            HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다");
    }

    private String login(String code) {
        return client.post().uri(KAKAO_LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(codeBody(code))
            .exchange()
            .expectStatus().isOk()
            .expectBody(LoginResponse.class)
            .returnResult()
            .getResponseBody()
            .accessToken();
    }

    // 발급받은 Access Token의 회원 ID(sub)
    private static Long memberIdOf(String accessToken) {
        return IssuedAccessToken.decode(accessToken).memberId();
    }

    private static String codeBody(String code) {
        return "{\"code\": \"" + code + "\"}";
    }

    private void expectProblem(String path, String body, HttpStatus status, String title) {
        client.post().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.instance").isEqualTo(path);
    }
}
