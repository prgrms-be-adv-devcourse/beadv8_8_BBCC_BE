package com.bbcc.kidly.boundedcontext.chatbot.out.search;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ChatbotSearchRestClientConfig {

    // 검색 서비스는 검색어 임베딩(OpenAI, 최대 약 3초)을 기다린 뒤 찾는다. 회원이 답을 기다리므로 그보다 조금만 더 기다린다
    @Bean
    public RestClient chatbotSearchRestClient() {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(factory).build();
    }
}
