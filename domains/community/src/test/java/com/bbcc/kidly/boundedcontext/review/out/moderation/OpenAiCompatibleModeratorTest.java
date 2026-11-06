package com.bbcc.kidly.boundedcontext.review.out.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * OpenAI 호환 채팅 API(Llama Guard) 어댑터: 요청 모양, 응답 해석(safe / unsafe + 분류 → 커뮤니티 이유), 실패 → UNDECIDED,
 * 키 · 본문을 로그에 남기지 않음.
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiCompatibleModeratorTest {

    private static final String KEY = "gsk_SECRET_" + System.nanoTime();
    private static final String MODEL = "meta-llama/llama-guard-4-12b";
    private static final String TEXT = "리뷰본문_" + System.nanoTime();

    private FakeChatServer server;
    private OpenAiCompatibleModerator moderator;

    @BeforeEach
    void start() {
        server = FakeChatServer.start();
        moderator = new OpenAiCompatibleModerator(server.baseUrl(), MODEL, KEY, Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("요청: {base-url}/chat/completions에 Bearer 키 · 모델 · 글 · 사진 주소를 한 요청으로 보낸다")
    void requestShape() {
        server.respond(200, FakeChatServer.verdict("safe"));

        moderator.moderate(new Content(TEXT, List.of("https://img.example/1.jpg", "https://img.example/2.png")));

        FakeChatServer.Received request = server.received().getFirst();
        assertThat(request.path()).isEqualTo("/openai/v1/chat/completions");
        assertThat(request.headers().get("Authorization")).containsExactly("Bearer " + KEY);
        assertThat(request.body()).contains("\"model\":\"" + MODEL + "\"")
            .contains(TEXT).contains("https://img.example/1.jpg").contains("https://img.example/2.png")
            .contains("image_url");
    }

    @Test
    @DisplayName("사진이 없으면 글만 보낸다")
    void textOnly() {
        server.respond(200, FakeChatServer.verdict("safe"));

        moderator.moderate(new Content(TEXT, List.of()));

        assertThat(server.received().getFirst().body()).contains(TEXT).doesNotContain("image_url");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"S1, VIOLENCE", "S2, ILLEGAL", "S3, SEXUAL", "S4, SEXUAL", "S7, PRIVACY", "S9, VIOLENCE",
        "S10, HATE", "S12, SEXUAL", "S5, OTHER", "S11, OTHER", "S14, OTHER", "S99, OTHER", "'S10,S1', HATE"})
    @DisplayName("unsafe + 분류는 커뮤니티 이유로 바꾼다(여러 개면 첫 분류, 모르는 분류는 OTHER)")
    void mapsCategories(String categories, ModerationReason reason) {
        server.respond(200, FakeChatServer.verdict("unsafe\\n" + categories));

        assertThat(moderator.moderate(new Content(TEXT, List.of()))).isEqualTo(Result.flagged(reason));
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @CsvSource(delimiter = '|', value = {
        "200|{\"choices\":[]}",
        "200|not json",
        "200|{\"choices\":[{\"message\":{\"content\":\"maybe\"}}]}",
        "200|{\"choices\":[{\"message\":{\"content\":\"unsafe\"}}]}",
        "401|{\"error\":{}}",
        "403|{\"error\":{}}",
        "400|{\"error\":{}}",
        "503|{}"})
    @DisplayName("이상한 응답 · 인증 실패 · 5xx는 UNDECIDED(UNAVAILABLE)")
    void oddResponsesAreUnavailable(int status, String body) {
        server.respond(status, body);

        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("연결이 안 되면 UNDECIDED(UNAVAILABLE)")
    void connectionRefused() {
        String baseUrl = server.baseUrl();
        server.stop();

        assertThat(new OpenAiCompatibleModerator(baseUrl, MODEL, KEY, Duration.ofMillis(500))
            .moderate(new Content(TEXT, List.of()))).isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("키 · 요청 · 응답 본문을 로그에 남기지 않는다(실패 때도)")
    void neverLogsSecretsOrBodies(CapturedOutput output) {
        server.respond(500, "{\"error\":\"" + TEXT + "\"}");
        moderator.moderate(new Content(TEXT, List.of()));
        server.respond(429, "{}");
        moderator.moderate(new Content(TEXT, List.of()));
        server.respondAfter(Duration.ofSeconds(2), 200, FakeChatServer.verdict("safe"));
        moderator.moderate(new Content(TEXT, List.of()));

        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain(TEXT);
        assertThat(moderator.toString()).doesNotContain(KEY);
    }

    @Test
    @DisplayName("키가 없으면 쓸 수 없는 검사기다(available false)")
    void unavailableWithoutKey() {
        assertThat(new OpenAiCompatibleModerator(server.baseUrl(), MODEL, "", Duration.ofSeconds(1)).available())
            .isFalse();
        assertThat(new OpenAiCompatibleModerator(server.baseUrl(), MODEL, " ", Duration.ofSeconds(1)).available())
            .isFalse();
        assertThat(moderator.available()).isTrue();
        assertThat(moderator.name()).isEqualTo("ai");
    }
}
