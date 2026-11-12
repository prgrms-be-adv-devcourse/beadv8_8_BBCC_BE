package com.bbcc.kidly.boundedcontext.chatbot.out.order;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 회원 주문을 부를 주문 서비스 주소(kidly.chatbot.order.uri, 환경 변수 KIDLY_ORDER_URI). 없으면 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("kidly.chatbot.order")
public record ChatbotOrderProperties(@NotBlank String uri) {
}
