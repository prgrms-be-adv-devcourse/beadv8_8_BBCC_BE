package com.bbcc.kidly.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.revocation.AccessTokenRevocations;
import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend.RecordedRequest;
import com.bbcc.kidly.gateway.support.TestTokens;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * Gateway가 밖에서 온 X-Member-* 헤더를 지우고, Bearer 토큰을 검증 · 무효화 확인한 뒤 회원 헤더를 붙여 넘기는지 본다(#608).
 */
@IntegrationTest
class AccessTokenFilterIntegrationTest extends GatewayTestSupport {

    // kidly-app으로 가는 공개 API. 목록(/api/v1/products)은 검색 서비스로 간다
    private static final String PATH = "/api/v1/products/1";
    private static final Duration VALIDITY = Duration.ofMinutes(30);

    @Autowired
    private AccessTokenRevocations revocations;

    @Test
    @DisplayName("밖에서 보낸 X-Member-* 헤더는 지우고 넘긴다")
    void removesForgedMemberHeaders() {
        client.get().uri(PATH)
            .header(MemberHeaders.MEMBER_ID, "1")
            .header(MemberHeaders.MEMBER_ROLES, "ADMIN")
            .header(MemberHeaders.MEMBER_NICKNAME, "admin")
            .exchange()
            .expectStatus().isOk();

        var received = received();
        assertThat(received.header(MemberHeaders.MEMBER_ID)).isEmpty();
        assertThat(received.header(MemberHeaders.MEMBER_ROLES)).isEmpty();
        assertThat(received.header(MemberHeaders.MEMBER_NICKNAME)).isEmpty();
    }

    @Test
    @DisplayName("헤더 이름의 대소문자를 바꿔 보낸 X-Member-* 헤더도 지운다")
    void removesForgedMemberHeadersInAnyCase() {
        client.get().uri(PATH)
            .header("x-member-id", "1")
            .header("x-member-roles", "ADMIN")
            .header("x-member-nickname", "admin")
            .exchange()
            .expectStatus().isOk();

        var received = received();
        assertThat(received.header(MemberHeaders.MEMBER_ID)).isEmpty();
        assertThat(received.header(MemberHeaders.MEMBER_ROLES)).isEmpty();
        assertThat(received.header(MemberHeaders.MEMBER_NICKNAME)).isEmpty();
    }

    @Test
    @DisplayName("토큰이 없으면 그대로 넘긴다")
    void passesWithoutToken() {
        client.get().uri(PATH).exchange()
            .expectStatus().isOk();

        assertThat(received().uri().getPath()).isEqualTo(PATH);
    }

    @Test
    @DisplayName("Bearer가 아닌 Authorization은 그대로 넘긴다")
    void passesNonBearerAuthorization() {
        client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "Basic abc")
            .exchange()
            .expectStatus().isOk();

        assertThat(received().header(HttpHeaders.AUTHORIZATION)).containsExactly("Basic abc");
    }

    @Test
    @DisplayName("정상 토큰이면 회원 ID · 역할 헤더를 붙이고 Authorization도 그대로 넘긴다")
    void addsMemberHeaders() {
        var now = Instant.now();
        var token = TestTokens.issue(7L, List.of("BUYER", "SELLER"), now, now.plus(VALIDITY));

        client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isOk();

        var received = received();
        assertThat(received.header(MemberHeaders.MEMBER_ID)).containsExactly("7");
        assertThat(received.header(MemberHeaders.MEMBER_ROLES)).containsExactly("BUYER,SELLER");
        assertThat(received.header(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + token);
    }

    @Test
    @DisplayName("정상 토큰과 함께 보낸 X-Member-* 헤더는 토큰 값으로 바꿔 하나만 넘긴다")
    void replacesForgedMemberHeadersWithTokenValues() {
        var now = Instant.now();
        var token = TestTokens.issue(7L, List.of("BUYER"), now, now.plus(VALIDITY));

        client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header(MemberHeaders.MEMBER_ID, "1")
            .header(MemberHeaders.MEMBER_ROLES, "ADMIN")
            .header(MemberHeaders.MEMBER_NICKNAME, "admin")
            .exchange()
            .expectStatus().isOk();

        var received = received();
        assertThat(received.header(MemberHeaders.MEMBER_ID)).containsExactly("7");
        assertThat(received.header(MemberHeaders.MEMBER_ROLES)).containsExactly("BUYER");
        assertThat(received.header(MemberHeaders.MEMBER_NICKNAME)).isEmpty();
    }

    @Test
    @DisplayName("토큰에 닉네임이 있으면 UTF-8 퍼센트 인코딩(공백 %20)으로 붙이고, 서비스처럼 URLDecoder로 풀면 원래 닉네임이다")
    void addsEncodedNicknameHeader() {
        var nickname = "키들리 엄마+아빠 100%";

        client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.issueWithNickname(7L, nickname))
            .header(MemberHeaders.MEMBER_NICKNAME, "admin")
            .exchange()
            .expectStatus().isOk();

        var encoded = received().header(MemberHeaders.MEMBER_NICKNAME);
        assertThat(encoded).singleElement().satisfies(value -> {
            assertThat(value).contains("%20").doesNotContain("+", " ");
            assertThat(URLDecoder.decode(value, StandardCharsets.UTF_8)).isEqualTo(nickname);
        });
    }

    @Test
    @DisplayName("토큰에 닉네임 클레임이 없으면 닉네임 헤더를 붙이지 않는다")
    void omitsNicknameHeaderWithoutClaim() {
        var now = Instant.now();

        bearer(TestTokens.issue(7L, List.of("BUYER"), now, now.plus(VALIDITY))).expectStatus().isOk();

        var received = received();
        assertThat(received.header(MemberHeaders.MEMBER_ID)).containsExactly("7");
        assertThat(received.header(MemberHeaders.MEMBER_NICKNAME)).isEmpty();
    }

    @Test
    @DisplayName("Bearer는 대소문자를 가리지 않는다: 대문자로 보낸 정상 토큰도 회원 헤더를 붙여 넘긴다")
    void acceptsBearerInAnyCase() {
        var now = Instant.now();
        var token = TestTokens.issue(7L, List.of("BUYER"), now, now.plus(VALIDITY));

        client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "BEARER " + token)
            .exchange()
            .expectStatus().isOk();

        assertThat(received().header(MemberHeaders.MEMBER_ID)).containsExactly("7");
    }

    @Test
    @DisplayName("소문자 bearer로 보낸 무효화된 토큰도 검증해 401이다(서비스는 대소문자를 가리지 않고 토큰으로 본다)")
    void rejectsRevokedTokenWithLowerCaseBearer() {
        var now = Instant.now();
        revocations.revoke(10L, now, now);
        var token = TestTokens.issue(10L, List.of("BUYER"), now.minusSeconds(5), now.plus(VALIDITY));

        expectUnauthorized(client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "bearer " + token)
            .exchange(), PATH);
    }

    @Test
    @DisplayName("서명은 맞지만 회원 ID(sub)가 숫자가 아닌 토큰은 401이다")
    void rejectsNonNumericSubject() {
        var now = Instant.now();

        expectUnauthorized(bearer(TestTokens.issueWithSubject("abc", now, now.plus(VALIDITY))), PATH);
    }

    @Test
    @DisplayName("만료된 지 시계 오차(60초)를 넘긴 토큰은 401이고 넘기지 않는다")
    void rejectsExpiredToken() {
        var now = Instant.now();
        var token = TestTokens.issue(7L, List.of("BUYER"), now.minus(VALIDITY).minusSeconds(120),
            now.minusSeconds(120));

        expectUnauthorized(bearer(token), PATH);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 401이다")
    void rejectsTokenSignedWithOtherKey() {
        expectUnauthorized(bearer(TestTokens.issueWithOtherKey(7L)), PATH);
    }

    @Test
    @DisplayName("형식이 깨진 토큰은 401이다")
    void rejectsMalformedToken() {
        expectUnauthorized(bearer("abc"), PATH);
    }

    @Test
    @DisplayName("무효화 전에 발급된 토큰은 401이다")
    void rejectsRevokedToken() {
        var now = Instant.now();
        revocations.revoke(8L, now, now);
        var token = TestTokens.issue(8L, List.of("BUYER"), now.minusSeconds(5), now.plus(VALIDITY));

        expectUnauthorized(bearer(token), PATH);
    }

    @Test
    @DisplayName("무효화 뒤에 발급된 토큰은 넘긴다")
    void passesTokenIssuedAfterRevocation() {
        var now = Instant.now();
        revocations.revoke(9L, now.minusSeconds(10), now);
        var token = TestTokens.issue(9L, List.of("BUYER"), now, now.plus(VALIDITY));

        bearer(token).expectStatus().isOk();

        assertThat(received().header(MemberHeaders.MEMBER_ID)).containsExactly("9");
    }

    private ResponseSpec bearer(String token) {
        return client.get().uri(PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange();
    }

    private RecordedRequest received() {
        return backend.lastRequest().orElseThrow(() -> new AssertionError("백엔드가 요청을 받지 않았습니다"));
    }
}
