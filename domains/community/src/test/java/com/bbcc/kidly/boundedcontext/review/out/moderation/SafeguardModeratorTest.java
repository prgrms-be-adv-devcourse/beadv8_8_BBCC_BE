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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정책 기반 안전 분류 모델 어댑터: 요청 모양(정책 = system, 리뷰 본문 = user, 사진 없음, 설정값), 응답 해석(violation · category →
 * 커뮤니티 결과), 실패 → UNDECIDED, 키 · 본문 · rationale을 로그에 남기지 않음.
 */
@ExtendWith(OutputCaptureExtension.class)
class SafeguardModeratorTest {

    static final String MODEL = "openai/gpt-oss-safeguard-20b";
    static final String POLICY = "# 정책_" + System.nanoTime() + "\n출력은 JSON 하나";
    private static final String KEY = "gsk_SECRET_" + System.nanoTime();
    private static final String TEXT = "리뷰본문_" + System.nanoTime();
    private static final String RATIONALE = "이유_" + System.nanoTime();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FakeChatServer server;
    private SafeguardModerator moderator;

    static SafeguardModerator.Settings settings(String baseUrl, String apiKey, Duration timeout) {
        return new SafeguardModerator.Settings(baseUrl, MODEL, apiKey, timeout, POLICY, 0.0, 1024, true, null);
    }

    @BeforeEach
    void start() {
        server = FakeChatServer.start();
        moderator = new SafeguardModerator(settings(server.baseUrl(), KEY, Duration.ofMillis(500)));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    @DisplayName("요청: Bearer 키, model · system=정책 · user=리뷰 본문(사진 없음) · response_format json_object · "
        + "temperature 0 · max_tokens 1024, reasoning_effort는 기본으로 보내지 않는다")
    void requestShape() {
        server.respond(200, FakeChatServer.reply("{\"violation\":0,\"category\":null}"));

        moderator.moderate(new Content(TEXT, List.of("https://img.example/1.jpg")));

        FakeChatServer.Received request = server.received().getFirst();
        assertThat(request.path()).isEqualTo("/openai/v1/chat/completions");
        assertThat(request.headers().get("Authorization")).containsExactly("Bearer " + KEY);
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.get("model").asString()).isEqualTo(MODEL);
        assertThat(body.get("messages")).hasSize(2);
        assertThat(body.get("messages").get(0).get("role").asString()).isEqualTo("system");
        assertThat(body.get("messages").get(0).get("content").asString()).isEqualTo(POLICY);
        assertThat(body.get("messages").get(1).get("role").asString()).isEqualTo("user");
        assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo(TEXT);
        assertThat(body.get("response_format").get("type").asString()).isEqualTo("json_object");
        assertThat(body.get("temperature").asDouble()).isZero();
        assertThat(body.get("max_tokens").asInt()).isEqualTo(1024);
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(request.body()).doesNotContain("img.example").doesNotContain("image_url")
            .doesNotContain("{{USER_INPUT}}");
    }

    @Test
    @DisplayName("설정으로 reasoning_effort를 켜고 response_format을 끄고 max_tokens · temperature를 바꿀 수 있다")
    void optionalSettings() {
        server.respond(200, FakeChatServer.reply("{\"violation\":0,\"category\":null}"));
        SafeguardModerator custom = new SafeguardModerator(new SafeguardModerator.Settings(server.baseUrl(), MODEL,
            KEY, Duration.ofMillis(500), POLICY, 0.2, 512, false, "low"));

        custom.moderate(new Content(TEXT, List.of()));

        JsonNode body = JSON.readTree(server.received().getFirst().body());
        assertThat(body.get("reasoning_effort").asString()).isEqualTo("low");
        assertThat(body.has("response_format")).isFalse();
        assertThat(body.get("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
        "{\"violation\":0,\"category\":null,\"rationale\":\"r\"}|PASSED|",
        "{\"violation\":0}|PASSED|",
        "{\"violation\":1,\"category\":\"PROFANITY\"}|FLAGGED|PROFANITY",
        "{\"violation\":1,\"category\":\"HATE\"}|FLAGGED|HATE",
        "{\"violation\":1,\"category\":\"SEXUAL\"}|FLAGGED|SEXUAL",
        "{\"violation\":1,\"category\":\"VIOLENCE\"}|FLAGGED|VIOLENCE",
        "{\"violation\":1,\"category\":\"ILLEGAL\"}|FLAGGED|ILLEGAL",
        "{\"violation\":1,\"category\":\"PRIVACY\"}|FLAGGED|PRIVACY",
        "{\"violation\":1,\"category\":null}|FLAGGED|OTHER",
        "{\"violation\":1}|FLAGGED|OTHER",
        "{\"violation\":1,\"category\":\"SPAM\"}|FLAGGED|OTHER",
        "{\"violation\":1,\"category\":\"hate\"}|FLAGGED|OTHER",
        "{\"violation\":1,\"category\":7}|FLAGGED|OTHER",
        "```json\\n{\"violation\":1,\"category\":\"HATE\"}\\n```|FLAGGED|HATE",
        "\\n {\"violation\":0}\\n |PASSED|"})
    @DisplayName("응답 content(JSON)의 violation · category를 커뮤니티 결과로 바꾼다(코드 펜스 · 공백은 벗긴다, \\n은 줄바꿈)")
    void mapsVerdicts(String content, String verdict, String reason) {
        server.respond(200, FakeChatServer.reply(content.replace("\\n", "\n")));

        Result expected = verdict.equals("PASSED") ? Result.passed()
            : Result.flagged(ModerationReason.valueOf(reason));
        assertThat(moderator.moderate(new Content(TEXT, List.of()))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "not json",
        "safe",
        "{}",
        "{\"violation\":2}",
        "{\"violation\":\"1\"}",
        "{\"violation\":true}",
        "{\"violation\":null}",
        "{\"violation\":0.5}",
        "[1]",
        "{\"violation\":0} 뒤에 글",
        "''"})
    @DisplayName("content가 JSON이 아니거나 violation이 없거나 0 · 1이 아니면 UNDECIDED(UNAVAILABLE)")
    void invalidContentIsUnavailable(String content) {
        server.respond(200, FakeChatServer.reply(content));

        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("응답이 잘리면(finish_reason length) 내용이 맞아 보여도 UNDECIDED(UNAVAILABLE)")
    void truncatedIsUnavailable() {
        server.respond(200, FakeChatServer.reply("{\"violation\":0,\"category\":null}", "length"));

        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("끝난 까닭이 없어도(null) content가 맞으면 해석한다. reasoning 같은 다른 칸은 무시한다")
    void ignoresOtherFields() {
        server.respond(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"violation\\\":1,\\\"category\\\":"
            + "\\\"PRIVACY\\\"}\",\"reasoning\":\"violation 0\"}}]}");

        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.flagged(ModerationReason.PRIVACY));
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @CsvSource(delimiter = '|', value = {
        "200|{\"choices\":[]}",
        "200|not json",
        "200|{\"choices\":[{\"message\":{\"content\":null}}]}",
        "200|{\"choices\":[{\"message\":null}]}",
        "400|{\"error\":{}}",
        "401|{\"error\":{}}",
        "403|{\"error\":{}}",
        "404|{\"error\":{}}",
        "500|{}",
        "503|{}"})
    @DisplayName("이상한 응답 · 4xx · 5xx는 UNDECIDED(UNAVAILABLE)")
    void oddResponsesAreUnavailable(int status, String body) {
        server.respond(status, body);

        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("429는 UNDECIDED(RATE_LIMITED), 시간 초과는 UNDECIDED(TIMEOUT)")
    void rateLimitAndTimeout() {
        server.respond(429, "{}");
        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.RATE_LIMITED));

        server.respondAfter(Duration.ofSeconds(2), 200, FakeChatServer.reply("{\"violation\":0}"));
        assertThat(moderator.moderate(new Content(TEXT, List.of())))
            .isEqualTo(Result.undecided(FailureCause.TIMEOUT));
    }

    @Test
    @DisplayName("연결이 안 되면 UNDECIDED(UNAVAILABLE)")
    void connectionRefused() {
        String baseUrl = server.baseUrl();
        server.stop();

        assertThat(new SafeguardModerator(settings(baseUrl, KEY, Duration.ofMillis(500)))
            .moderate(new Content(TEXT, List.of()))).isEqualTo(Result.undecided(FailureCause.UNAVAILABLE));
    }

    @Test
    @DisplayName("판단 못 함 로그에는 실패 종류 · HTTP 상태 · 모델 이름만 남기고 키 · 본문 · 정책 · rationale은 남기지 않는다")
    void logsCauseStatusModelOnly(CapturedOutput output) {
        server.respond(500, "{\"error\":\"" + TEXT + "\"}");
        moderator.moderate(new Content(TEXT, List.of()));
        server.respond(429, "{}");
        moderator.moderate(new Content(TEXT, List.of()));
        server.respond(200, FakeChatServer.reply("{\"violation\":3,\"rationale\":\"" + RATIONALE + "\"}"));
        moderator.moderate(new Content(TEXT, List.of()));
        server.respond(200, FakeChatServer.reply("{\"violation\":0,\"rationale\":\"" + RATIONALE + "\"}", "length"));
        moderator.moderate(new Content(TEXT, List.of()));
        server.respond(200, FakeChatServer.reply("{\"violation\":1,\"category\":\"HATE\",\"rationale\":\""
            + RATIONALE + "\"}"));
        moderator.moderate(new Content(TEXT, List.of()));
        server.respondAfter(Duration.ofSeconds(2), 200, FakeChatServer.reply("{\"violation\":0}"));
        moderator.moderate(new Content(TEXT, List.of()));

        assertThat(output.getAll()).contains("status=500").contains("status=429").contains("cause=TIMEOUT")
            .contains("model=" + MODEL)
            .doesNotContain(KEY).doesNotContain(TEXT).doesNotContain(RATIONALE).doesNotContain("정책_");
        assertThat(moderator.toString()).doesNotContain(KEY).contains(MODEL);
    }

    @Test
    @DisplayName("이름은 ai, 키가 없으면 쓸 수 없는 검사기다(available false)")
    void unavailableWithoutKey() {
        assertThat(new SafeguardModerator(settings(server.baseUrl(), "", Duration.ofSeconds(1))).available())
            .isFalse();
        assertThat(new SafeguardModerator(settings(server.baseUrl(), " ", Duration.ofSeconds(1))).available())
            .isFalse();
        assertThat(moderator.available()).isTrue();
        assertThat(moderator.name()).isEqualTo("ai");
    }
}
