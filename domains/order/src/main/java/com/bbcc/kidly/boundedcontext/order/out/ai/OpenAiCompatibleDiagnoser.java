package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.config.OrderAiProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 호환 Chat Completions로 멈춘 주문을 진단한다(#926). Groq(기본) · OpenAI 등은 base-url · model · 키만 바꾼다.
 * POST {base-url}/chat/completions, JSON 모드(response_format json_object), 온도 0, 출력 상한(max_completion_tokens).
 * 응답 본문은 {@link #MAX_RESPONSE_BYTES}까지만 읽고 넘으면 BAD_RESPONSE. 답(choices[0].message.content)은
 * {@link AiDiagnosisPrompt}가 해석한다. 서킷브레이커가 열려 있으면 부르지 않는다(NOT_CALLED).
 * 실패는 예외 대신 FAILED 결과로 돌려주고 WARN 한 줄(이유 코드 · HTTP 상태만, 본문 · 키 없음)을 남긴다.
 */
@Slf4j
public final class OpenAiCompatibleDiagnoser implements StuckOrderDiagnoser {

    static final String COMPLETIONS_PATH = "/chat/completions";
    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final RestClient restClient;
    private final AiDiagnosisPrompt prompt;
    private final AiCircuitBreaker circuitBreaker;
    private final JsonMapper jsonMapper;
    private final String provider;
    private final String model;
    private final int maxOutputTokens;

    private OpenAiCompatibleDiagnoser(RestClient restClient, AiDiagnosisPrompt prompt,
        AiCircuitBreaker circuitBreaker, JsonMapper jsonMapper, String provider, OrderAiProperties properties) {
        this.restClient = restClient;
        this.prompt = prompt;
        this.circuitBreaker = circuitBreaker;
        this.jsonMapper = jsonMapper;
        this.provider = provider;
        this.model = properties.model();
        this.maxOutputTokens = properties.maxOutputTokens();
    }

    /**
     * @param builder 타임아웃을 정한 빌더(테스트는 가짜 서버를 붙인다)
     */
    public static OpenAiCompatibleDiagnoser create(OrderAiProperties properties, RestClient.Builder builder,
        AiDiagnosisPrompt prompt, JsonMapper jsonMapper, Clock clock) {
        String baseUrl = properties.baseUrl().endsWith("/")
            ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1) : properties.baseUrl();
        RestClient restClient = builder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + properties.apiKey())
            .build();
        return new OpenAiCompatibleDiagnoser(restClient, prompt,
            AiCircuitBreaker.create(properties.circuitBreaker(), clock), jsonMapper, hostOf(baseUrl), properties);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Diagnosis diagnose(Request request) {
        try {
            Diagnosis diagnosis = circuitBreaker.callIfPermitted(() -> call(request));
            return diagnosis == null ? Diagnosis.notCalled("CIRCUIT_OPEN") : diagnosis;
        } catch (AiDiagnosisException e) {
            log.warn("AI 진단 실패: provider={} model={} reason={}", provider, model, e.getMessage());
            return Diagnosis.failed(e.reason(), provider, model);
        }
    }

    private Diagnosis call(Request request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", prompt.messages(request));
        body.put("temperature", 0);
        body.put("max_completion_tokens", maxOutputTokens);
        body.put("response_format", Map.of("type", "json_object"));
        String raw;
        try {
            raw = restClient.post()
                .uri(COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((httpRequest, httpResponse) -> {
                    // 오류 응답의 본문은 읽지 않는다(키 · 요청 값이 되돌아올 수 있음)
                    if (httpResponse.getStatusCode().isError()) {
                        throw new AiDiagnosisException(AiDiagnosisException.HTTP_ERROR,
                            String.valueOf(httpResponse.getStatusCode().value()));
                    }
                    return readLimited(httpResponse.getBody());
                });
        } catch (ResourceAccessException e) {
            throw new AiDiagnosisException(isTimeout(e) ? AiDiagnosisException.TIMEOUT : AiDiagnosisException.ERROR,
                e.getCause() == null ? null : e.getCause().getClass().getSimpleName());
        } catch (RestClientException e) {
            throw new AiDiagnosisException(AiDiagnosisException.ERROR, e.getClass().getSimpleName());
        }
        JsonNode response;
        try {
            response = jsonMapper.readTree(raw);
        } catch (JacksonException e) {
            throw AiDiagnosisException.badResponse("body");
        }
        JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isString()) {
            throw AiDiagnosisException.badResponse("content");
        }
        return prompt.parse(content.asString(), provider, model);
    }

    // 상한을 넘는 본문은 끝까지 읽지 않고 BAD_RESPONSE(메모리 · 저장 보호)
    private static String readLimited(InputStream body) throws IOException {
        if (body == null) {
            throw AiDiagnosisException.badResponse("empty");
        }
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw AiDiagnosisException.badResponse("too-large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    // 메모에 남길 답한 곳: base-url의 호스트(예: api.groq.com). 경로 · 키는 넣지 않는다
    private static String hostOf(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null ? "openai-compatible" : host;
        } catch (IllegalArgumentException e) {
            return "openai-compatible";
        }
    }
}
