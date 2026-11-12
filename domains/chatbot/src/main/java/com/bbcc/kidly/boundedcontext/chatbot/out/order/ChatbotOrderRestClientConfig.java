package com.bbcc.kidly.boundedcontext.chatbot.out.order;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class ChatbotOrderRestClientConfig {

    // 주문 조회는 DB만 읽는다. 회원이 답을 기다리므로 짧게 기다린다
    @Bean
    public RestClient chatbotOrderRestClient() {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(factory).build();
    }
}
