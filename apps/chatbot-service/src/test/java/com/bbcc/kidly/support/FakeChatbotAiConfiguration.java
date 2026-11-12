package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.chatbot.FakeChatModel;
import com.bbcc.kidly.boundedcontext.chatbot.FakeEmbeddingModel;
import com.bbcc.kidly.boundedcontext.chatbot.FakeMemberOrderHistory;
import com.bbcc.kidly.boundedcontext.chatbot.FakeProductSearcher;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트 컨텍스트의 chat · 임베딩 모델과 상품 검색 · 회원 주문 조회. 테스트 프로필은 OpenAI 키를 비워 OpenAI 모델 빈이 없으므로 이 가짜만 뜬다.
 * 상품 검색 · 주문 조회는 다른 서비스 없이 답하도록 HTTP 구현 대신 가짜를 쓴다(HTTP 구현은 HttpProductSearcherTest ·
 * HttpMemberOrderHistoryTest가 본다).
 * 모든 통합 · 기능 테스트가 같이 써 컨텍스트가 하나로 재사용된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeChatbotAiConfiguration {

    @Bean
    ChatModel fakeChatModel() {
        return new FakeChatModel();
    }

    @Bean
    EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    @Primary
    FakeProductSearcher fakeProductSearcher() {
        return new FakeProductSearcher();
    }

    @Bean
    @Primary
    FakeMemberOrderHistory fakeMemberOrderHistory() {
        return new FakeMemberOrderHistory();
    }
}
