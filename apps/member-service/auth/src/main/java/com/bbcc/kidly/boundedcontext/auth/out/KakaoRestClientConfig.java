package com.bbcc.kidly.boundedcontext.auth.out;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoRestClientConfig {

    // JDK HttpClient를 쓴다. HttpURLConnection은 POST에 401이 오면 본문을 버려서 카카오 오류 코드가 로그에 남지 않는다
    @Bean
    public RestClient kakaoRestClient() {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
