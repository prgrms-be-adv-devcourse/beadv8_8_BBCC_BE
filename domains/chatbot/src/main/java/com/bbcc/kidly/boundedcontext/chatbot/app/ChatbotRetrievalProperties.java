package com.bbcc.kidly.boundedcontext.chatbot.app;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 답변 근거 찾기 설정(kidly.chatbot.retrieval.*). 틀리면 기동에 실패한다.
 *
 * @param topK 답에 넣을 조각 수 상한. 1 ~ 20
 * @param minScore 근거로 쓸 최소 코사인 유사도. 넘는 조각이 없으면 AI를 부르지 않고 모른다고 답한다
 */
@Validated
@ConfigurationProperties("kidly.chatbot.retrieval")
public record ChatbotRetrievalProperties(
    @Min(1) @Max(20) int topK,
    @DecimalMin("0.0") @DecimalMax("1.0") double minScore) {
}
