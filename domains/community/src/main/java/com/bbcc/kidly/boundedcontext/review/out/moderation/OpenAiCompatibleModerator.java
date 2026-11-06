package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI 호환 채팅 API(Chat Completions)로 글과 사진을 한 요청에 보내 검사한다(이름 ai). 기본은 Groq의 Llama Guard 4이고,
 * 같은 호환 API면 주소 · 모델 · 키 · 시간만 설정으로 바꾼다. 응답(safe / unsafe + 분류)의 해석과 분류 → 커뮤니티 이유 대응은
 * 이 클래스 안에서만 한다. 429는 한도 초과, 시간을 넘기면 시간 초과, 그 밖의 실패 · 이상한 응답은 장애(모두 UNDECIDED).
 * 키 · 요청 · 응답 본문은 로그 · 예외에 남기지 않는다(실패 원인 · 상태 코드만).
 */
@Slf4j
public class OpenAiCompatibleModerator implements ModerationAdapter {

    // Llama Guard 4의 위험 분류(S1~S14) → 커뮤니티 이유. 없는 분류는 OTHER
    private static final Map<String, ModerationReason> CATEGORIES = Map.ofEntries(
        Map.entry("S1", ModerationReason.VIOLENCE),
        Map.entry("S2", ModerationReason.ILLEGAL),
        Map.entry("S3", ModerationReason.SEXUAL),
        Map.entry("S4", ModerationReason.SEXUAL),
        Map.entry("S7", ModerationReason.PRIVACY),
        Map.entry("S9", ModerationReason.VIOLENCE),
        Map.entry("S10", ModerationReason.HATE),
        Map.entry("S12", ModerationReason.SEXUAL));

    private final RestClient restClient;
    private final String model;
    private final boolean hasKey;

    public OpenAiCompatibleModerator(String baseUrl, String model, String apiKey, Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.hasKey = apiKey != null && !apiKey.isBlank();
        this.model = model;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (hasKey ? apiKey.strip() : ""))
            .build();
    }

    @Override
    public String name() {
        return "ai";
    }

    @Override
    public boolean available() {
        return hasKey;
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
            log.warn("AI 리뷰 검사를 하지 못했습니다. status={}", status);
            return Result.undecided(status == 429 ? FailureCause.RATE_LIMITED : FailureCause.UNAVAILABLE);
        } catch (RuntimeException e) {
            boolean timeout = isTimeout(e);
            log.warn("AI 리뷰 검사를 하지 못했습니다. cause={} error={}", timeout ? "TIMEOUT" : "UNAVAILABLE",
                e.getClass().getSimpleName());
            return Result.undecided(timeout ? FailureCause.TIMEOUT : FailureCause.UNAVAILABLE);
        }
    }

    private Map<String, Object> request(Content content) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", content.text()));
        content.imageUrls().forEach(url -> parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url))));
        return Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", parts)));
    }

    private static Result interpret(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
            || response.choices().getFirst().message() == null
            || response.choices().getFirst().message().content() == null) {
            return unexpected();
        }
        List<String> lines = response.choices().getFirst().message().content().strip().lines()
            .map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty()) {
            return unexpected();
        }
        String verdict = lines.getFirst().toLowerCase(Locale.ROOT);
        if (verdict.equals("safe")) {
            return Result.passed();
        }
        if (verdict.equals("unsafe") && lines.size() > 1) {
            String first = lines.get(1).split(",")[0].strip().toUpperCase(Locale.ROOT);
            return Result.flagged(CATEGORIES.getOrDefault(first, ModerationReason.OTHER));
        }
        return unexpected();
    }

    private static Result unexpected() {
        log.warn("AI 리뷰 검사 응답을 해석하지 못했습니다");
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
        return "OpenAiCompatibleModerator[model=" + model + ", key=" + (hasKey ? "있음" : "없음") + "]";
    }

    // 응답에서 쓰는 칸만 읽는다(모르는 칸은 무시)
    record ChatResponse(List<Choice> choices) {
    }

    record Choice(Message message) {
    }

    record Message(String content) {
    }
}
