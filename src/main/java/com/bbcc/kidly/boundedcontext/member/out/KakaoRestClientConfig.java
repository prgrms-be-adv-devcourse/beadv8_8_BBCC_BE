package com.bbcc.kidly.boundedcontext.member.out;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoRestClientConfig {

    // 카카오가 느려도 로그인 요청이 오래 붙잡히지 않게 한다
    @Bean
    public RestClient kakaoRestClient() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
