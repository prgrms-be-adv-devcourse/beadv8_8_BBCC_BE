package com.bbcc.kidly.boundedcontext.review.out.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 리뷰 검사기 설정(kidly.community.moderation.*). 주기 · 건수 · 재시도는 app의 ReviewModerationProperties가 같은 키 아래에서
 * 따로 읽는다. 값이 비었거나 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 *
 * @param moderators 쓸 검사기 이름(부르는 순서). banned-words · ai
 * @param bannedWords 금칙어 목록(대소문자 · 공백 · 특수문자 무시). 운영 중 값만 바꿔 조절한다
 * @param ai 정책 기반 안전 분류 모델 검사기(OpenAI 호환 채팅 API)
 */
@Validated
@ConfigurationProperties("kidly.community.moderation")
public record CommunityModerationProperties(
    @NotEmpty List<@NotBlank String> moderators,
    List<String> bannedWords,
    @NotNull @Valid Ai ai) {

    public CommunityModerationProperties {
        bannedWords = bannedWords == null ? List.of() : List.copyOf(bannedWords);
    }

    /**
     * @param baseUrl OpenAI 호환 API 주소(예: https://api.groq.com/openai/v1). 뒤에 /chat/completions를 붙여 부른다
     * @param model 정책 기반 안전 분류 모델 이름(기본 openai/gpt-oss-safeguard-20b)
     * @param apiKey 키. 환경변수로만 받는다. 비면 AI 검사기를 빼고 금칙어만 쓴다. 로그 · 응답에 남기지 않는다
     * @param timeout 요청 하나의 연결 · 응답 시간 한도
     * @param policy 정책 파일 위치(system 메시지, 기본 classpath:moderation/review-policy.txt). 없거나 비면 기동 실패
     * @param temperature 보낼 temperature(기본 0)
     * @param maxTokens 보낼 max_tokens(기본 1024). 추론 토큰도 이 상한에 함께 잡히므로 줄이면 응답이 잘릴 수 있다
     * @param jsonResponse response_format json_object를 보낼지(기본 true)
     * @param reasoningEffort 보낼 reasoning_effort(low · medium · high). 비면 보내지 않는다(기본)
     */
    public record Ai(
        @NotBlank String baseUrl,
        @NotBlank String model,
        String apiKey,
        @NotNull @DurationMin(millis = 1) Duration timeout,
        String policy,
        Double temperature,
        @Min(1) Integer maxTokens,
        Boolean jsonResponse,
        String reasoningEffort) {

        public static final String DEFAULT_POLICY = "classpath:moderation/review-policy.txt";

        public Ai {
            policy = policy == null || policy.isBlank() ? DEFAULT_POLICY : policy.strip();
            temperature = temperature == null ? 0.0 : temperature;
            maxTokens = maxTokens == null ? 1024 : maxTokens;
            jsonResponse = jsonResponse == null || jsonResponse;
            reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort.strip();
        }

        @Override
        public String toString() {
            return "Ai[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + (apiKey == null || apiKey.isBlank()
                ? "없음" : "있음") + ", timeout=" + timeout + ", policy=" + policy + ", temperature=" + temperature
                + ", maxTokens=" + maxTokens + ", jsonResponse=" + jsonResponse + ", reasoningEffort="
                + reasoningEffort + "]";
        }
    }
}
