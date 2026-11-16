package com.bbcc.kidly.boundedcontext.chatbot.app;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 질문 하나에 답하는 설정(kidly.chatbot.answer.*). 틀리면 기동에 실패한다.
 *
 * @param timeBudget 질문 하나(질문 임베딩 · 도구 · 답 만들기)에 쓰는 시간 상한. 넘으면 남은 OpenAI 호출을 멈추고 503 AI_TIMEOUT으로
 *                   답한다. Gateway 응답 제한(55초)보다 짧아야 회원이 Gateway 504 대신 이 오류를 받는다
 */
@Validated
@ConfigurationProperties("kidly.chatbot.answer")
public record ChatbotAnswerProperties(@NotNull Duration timeBudget) {
}
