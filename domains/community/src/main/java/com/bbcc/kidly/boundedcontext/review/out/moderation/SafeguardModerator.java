package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정책 기반 안전 분류 모델(기본 Groq openai/gpt-oss-safeguard-20b)로 리뷰 글을 검사한다(이름 ai). OpenAI 호환 Chat Completions에
 * 정책을 system 메시지, 리뷰 본문을 user 메시지로 보낸다. 사진은 보내지 않는다(텍스트 전용 모델).
 * 응답 content의 JSON {@code {"violation": 0|1, "category": 코드|null, "rationale": ...}}을 이 클래스 안에서만 해석한다:
 * 0 → 통과, 1 → 걸림(같은 이름의 이유, 없거나 모르는 값은 OTHER). JSON이 아님 · 형식 틀림 · 잘림(finish_reason length) · 4xx · 5xx ·
 * 연결 실패는 장애, 429는 한도 초과, 시간을 넘기면 시간 초과(모두 UNDECIDED). rationale은 쓰지 않는다.
 * 키 · 요청 · 응답 본문 · 정책 · rationale은 로그 · 예외에 남기지 않는다(실패 종류 · HTTP 상태 · 모델 이름만).
 */
@Slf4j
public class SafeguardModerator implements ModerationAdapter {

    // 정책이 정한 걸림 분류 코드(같은 이름의 ModerationReason). 그 밖의 값은 OTHER
    private static final Set<String> CATEGORIES = Set.of("PROFANITY", "HATE", "SEXUAL", "VIOLENCE", "ILLEGAL",
        "PRIVACY");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param policy 정책 전문(system 메시지)
     * @param jsonResponse response_format json_object를 보낼지
     * @param reasoningEffort 보낼 reasoning_effort. null이면 보내지 않는다
     */
    public record Settings(String baseUrl, String model, String apiKey, Duration timeout, String policy,
                           double temperature, int maxTokens, boolean jsonResponse, String reasoningEffort) {

        boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Settings[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + (hasKey() ? "있음" : "없음")
                + ", timeout=" + timeout + ", policy=" + (policy == null ? 0 : policy.length()) + "자"
                + ", temperature=" + temperature + ", maxTokens=" + maxTokens + ", jsonResponse=" + jsonResponse
                + ", reasoningEffort=" + reasoningEffort + "]";
        }
    }

    private final Settings settings;
    private final RestClient restClient;

    public SafeguardModerator(Settings settings) {
        this.settings = settings;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
        requestFactory.setReadTimeout(settings.timeout());
        this.restClient = RestClient.builder()
            .baseUrl(settings.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (settings.hasKey() ? settings.apiKey().strip() : ""))
            .build();
    }

    public Settings settings() {
        return settings;
    }

    @Override
    public String name() {
        return "ai";
    }

    @Override
    public boolean available() {
        return settings.hasKey();
    }

    @Override
    public String unavailableNotice() {
        return "AI 검사 꺼짐(키 없음)";
    }

    @Override
    public Result moderate(Content content) {
        try {
            ChatResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request(content))
                .retrieve()
                .body(ChatResponse.class);
            return interpret(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            FailureCause cause = status == 429 ? FailureCause.RATE_LIMITED : FailureCause.UNAVAILABLE;
            log.warn("AI 리뷰 검사를 하지 못했습니다. cause={} status={} model={}", cause, status, settings.model());
            return Result.undecided(cause);
        } catch (RuntimeException e) {
            FailureCause cause = isTimeout(e) ? FailureCause.TIMEOUT : FailureCause.UNAVAILABLE;
            log.warn("AI 리뷰 검사를 하지 못했습니다. cause={} error={} model={}", cause, e.getClass().getSimpleName(),
                settings.model());
            return Result.undecided(cause);
        }
    }

    // 정책 = system, 리뷰 본문 = user. 사진은 보내지 않는다
    private Map<String, Object> request(Content content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("messages", List.of(Map.of("role", "system", "content", settings.policy()),
            Map.of("role", "user", "content", content.text())));
        if (settings.jsonResponse()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("temperature", settings.temperature());
        body.put("max_tokens", settings.maxTokens());
        if (settings.reasoningEffort() != null) {
            body.put("reasoning_effort", settings.reasoningEffort());
        }
        return body;
    }

    private Result interpret(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
            || response.choices().getFirst().message() == null
            || response.choices().getFirst().message().content() == null) {
            return unexpected("empty");
        }
        Choice choice = response.choices().getFirst();
        // 출력 상한에 걸려 잘렸으면 내용이 맞아 보여도 믿지 않는다
        if ("length".equals(choice.finishReason())) {
            return unexpected("truncated");
        }
        JsonNode verdict;
        try {
            verdict = JSON.readTree(stripFence(choice.message().content()));
        } catch (JacksonException e) {
            return unexpected("not_json");
        }
        JsonNode violation = verdict == null || !verdict.isObject() ? null : verdict.get("violation");
        if (violation == null || !violation.isIntegralNumber()) {
            return unexpected("invalid_format");
        }
        if (violation.asInt() == 0) {
            return Result.passed();
        }
        if (violation.asInt() == 1) {
            JsonNode category = verdict.get("category");
            String code = category != null && category.isString() ? category.asString() : null;
            return Result.flagged(code != null && CATEGORIES.contains(code) ? ModerationReason.valueOf(code)
                : ModerationReason.OTHER);
        }
        return unexpected("invalid_format");
    }

    // 앞뒤 공백과 마크다운 코드 펜스(```json … ```)를 벗긴다
    private static String stripFence(String content) {
        String text = content.strip();
        if (text.startsWith("```") && text.endsWith("```") && text.length() >= 6) {
            text = text.substring(3, text.length() - 3).strip();
            if (text.startsWith("json")) {
                text = text.substring(4).strip();
            }
        }
        return text;
    }

    private Result unexpected(String kind) {
        log.warn("AI 리뷰 검사 응답을 해석하지 못했습니다. cause={} kind={} model={}", FailureCause.UNAVAILABLE, kind,
            settings.model());
        return Result.undecided(FailureCause.UNAVAILABLE);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "SafeguardModerator[model=" + settings.model() + ", key=" + (settings.hasKey() ? "있음" : "없음") + "]";
    }

    // 응답에서 쓰는 칸만 읽는다(reasoning 등 모르는 칸은 무시)
    record ChatResponse(List<Choice> choices) {
    }

    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    record Message(String content) {
    }
}
