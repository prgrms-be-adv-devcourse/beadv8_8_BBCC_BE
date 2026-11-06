package com.bbcc.kidly.boundedcontext.review.out.moderation;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * OpenAI 호환 채팅 API 검사기를 로컬 가짜 HTTP 서버에 붙여 상황을 만든다.
 */
class OpenAiCompatibleModeratorContractTest extends ContentModeratorContractTest {

    private FakeChatServer server;

    @BeforeEach
    void start() {
        server = FakeChatServer.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Override
    protected ModerationAdapter adapterFor(Scenario scenario) {
        switch (scenario) {
            case CLEAN -> server.respond(200, FakeChatServer.verdict("safe"));
            case VIOLATING -> server.respond(200, FakeChatServer.verdict("unsafe\\nS10"));
            case FAILURE -> server.respond(500, "{}");
            case TIMEOUT -> server.respondAfter(Duration.ofSeconds(2), 200, FakeChatServer.verdict("safe"));
            case RATE_LIMIT -> server.respond(429, "{\"error\":{\"message\":\"rate limit\"}}");
            default -> throw new IllegalArgumentException();
        }
        return new OpenAiCompatibleModerator(server.baseUrl(), "meta-llama/llama-guard-4-12b", "test-key",
            Duration.ofMillis(500));
    }
}
