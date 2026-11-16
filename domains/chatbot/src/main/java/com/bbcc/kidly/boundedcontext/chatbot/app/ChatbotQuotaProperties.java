package com.bbcc.kidly.boundedcontext.chatbot.app;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 챗봇 비용 상한(kidly.chatbot.quota.*). 틀리면 기동에 실패한다.
 *
 * @param dailyQuestions 서비스 전체에서 하루(한국 시간)에 받는 질문 수. 넘으면 OpenAI를 부르지 않고 503 AI_DAILY_LIMIT_REACHED. 0이면 질문을 받지 않는다
 * @param historyChars 이전 대화에서 AI에 넘기는 글자 수 상한(모든 턴 합). 넘으면 오래된 턴부터 뺀다
 */
@Validated
@ConfigurationProperties("kidly.chatbot.quota")
public record ChatbotQuotaProperties(@Min(0) int dailyQuestions, @Min(0) int historyChars) {
}
