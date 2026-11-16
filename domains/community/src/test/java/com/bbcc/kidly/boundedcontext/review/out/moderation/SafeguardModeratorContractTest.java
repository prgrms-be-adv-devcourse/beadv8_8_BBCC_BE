package com.bbcc.kidly.boundedcontext.review.out.moderation;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * 정책 기반 안전 분류 모델(Groq gpt-oss-safeguard) 검사기를 로컬 가짜 HTTP 서버에 붙여 상황을 만든다.
 */
class SafeguardModeratorContractTest extends ContentModeratorContractTest {

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
            case CLEAN -> server.respond(200, FakeChatServer.reply("{\"violation\":0,\"category\":null}"));
            case VIOLATING -> server.respond(200, FakeChatServer.reply("{\"violation\":1,\"category\":\"HATE\"}"));
            case FAILURE -> server.respond(500, "{}");
            case TIMEOUT -> server.respondAfter(Duration.ofSeconds(2), 200,
                FakeChatServer.reply("{\"violation\":0,\"category\":null}"));
            case RATE_LIMIT -> server.respond(429, "{\"error\":{\"message\":\"rate limit\"}}");
            default -> throw new IllegalArgumentException();
        }
        return new SafeguardModerator(SafeguardModeratorTest.settings(server.baseUrl(), "test-key",
            Duration.ofMillis(500)));
    }
}
