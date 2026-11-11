package com.bbcc.kidly.boundedcontext.chatbot.out.search;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 상품 검색을 부를 검색 서비스 주소(kidly.chatbot.search.uri, 환경 변수 KIDLY_SEARCH_URI). 없으면 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("kidly.chatbot.search")
public record ChatbotSearchProperties(@NotBlank String uri) {
}
