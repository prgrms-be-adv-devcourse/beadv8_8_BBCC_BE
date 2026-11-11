package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import com.bbcc.kidly.gateway.support.TestTokens;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 챗봇 질문(POST /api/v1/chatbot/messages)을 회원마다 분당 10번까지 넘기고, 넘으면 429로 막는지 본다(#1191).
 * 컨텍스트를 다른 테스트와 같이 쓰므로(한도도 같이 쓴다) 테스트마다 처음 쓰는 회원 ID로 보낸다.
 * 한도는 6초에 하나씩 다시 차므로, 막힐 때까지 보내고 통과한 수를 범위로 본다.
 */
@IntegrationTest
class ChatRateLimitIntegrationTest extends GatewayTestSupport {

    private static final int CAPACITY = 10;
    private static final String MESSAGES = "/api/v1/chatbot/messages";
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired
    @Qualifier(RecordingBackendConfiguration.CHATBOT)
    private RecordingBackend chatbotBackend;

    @Test
    @DisplayName("같은 회원의 질문은 10번까지 넘기고, 넘으면 429 ProblemDetail과 Retry-After로 막고 넘기지 않는다")
    void limitsByMember() {
        var member = token(uniqueId());
        assertThat(exhaust(() -> ask(member))).isBetween(CAPACITY, CAPACITY + 2);
        chatbotBackend.reset();

        var response = ask(member);

        expectProblem(response, HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다", "잠시 후 다시 시도해 주세요.", MESSAGES);
        // 하나가 다시 차는 데 60초 / 10 = 6초
        response.expectHeader().valueMatches(HttpHeaders.RETRY_AFTER, "[1-6]");
        assertThat(chatbotBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("다른 회원은 따로 센다")
    void countsMembersSeparately() {
        var member = token(uniqueId());
        exhaust(() -> ask(member));

        ask(member).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ask(token(uniqueId())).expectStatus().isOk();
    }

    @Test
    @DisplayName("관리자 챗봇 경로와 챗봇 질문이 아닌 요청은 세지 않는다")
    void doesNotLimitOtherChatbotRequests() {
        var now = Instant.now();
        var admin = TestTokens.issueAdmin(uniqueId(), now, now.plusSeconds(1800));
        for (int i = 0; i <= CAPACITY * 2; i++) {
            client.post().uri("/api/v1/admin/chatbot/messages").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isOk();
        }

        var member = token(uniqueId());
        for (int i = 0; i <= CAPACITY * 2; i++) {
            client.get().uri("/api/v1/chatbot/conversations/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
                .exchange().expectStatus().isOk();
        }
        ask(member).expectStatus().isOk();
    }

    @Test
    @DisplayName("챗봇 질문 한도를 다 써도 검색 한도에는 영향이 없다")
    void doesNotAffectSearchLimit() {
        var member = token(uniqueId());
        exhaust(() -> ask(member));

        client.get().uri("/api/v1/products?keyword=물놀이").header(HttpHeaders.AUTHORIZATION, "Bearer " + member)
            .exchange().expectStatus().isOk();
    }

    // 429가 날 때까지 보내고 통과한 수를 돌려준다
    private static int exhaust(Supplier<ResponseSpec> request) {
        for (int passed = 0; passed <= CAPACITY + 5; passed++) {
            var status = request.get().returnResult(Void.class).getStatus();
            if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return passed;
            }
            assertThat(status.is2xxSuccessful()).isTrue();
        }
        return Integer.MAX_VALUE;
    }

    private ResponseSpec ask(String token) {
        return client.post().uri(MESSAGES).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private static String token(long memberId) {
        var now = Instant.now();
        return TestTokens.issue(memberId, List.of("USER"), now, now.plusSeconds(1800));
    }

    private static long uniqueId() {
        return 200_000L + NEXT.incrementAndGet();
    }
}
