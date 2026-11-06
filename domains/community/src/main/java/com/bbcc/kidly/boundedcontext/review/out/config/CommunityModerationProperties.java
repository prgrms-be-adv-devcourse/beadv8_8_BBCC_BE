package com.bbcc.kidly.boundedcontext.review.out.config;

import jakarta.validation.Valid;
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
 * @param ai OpenAI 호환 채팅 API 검사기
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
     * @param model 모델 이름(예: meta-llama/llama-guard-4-12b)
     * @param apiKey 키. 환경변수로만 받는다. 비면 AI 검사기를 빼고 금칙어만 쓴다. 로그 · 응답에 남기지 않는다
     * @param timeout 요청 하나의 연결 · 응답 시간 한도
     */
    public record Ai(
        @NotBlank String baseUrl,
        @NotBlank String model,
        String apiKey,
        @NotNull @DurationMin(millis = 1) Duration timeout) {

        @Override
        public String toString() {
            return "Ai[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + (apiKey == null || apiKey.isBlank()
                ? "없음" : "있음") + ", timeout=" + timeout + "]";
        }
    }
}
