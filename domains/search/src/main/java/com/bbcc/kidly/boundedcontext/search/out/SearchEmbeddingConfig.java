package com.bbcc.kidly.boundedcontext.search.out;

import com.bbcc.kidly.boundedcontext.search.app.port.TextEmbedder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 의미 검색 임베딩 API 클라이언트를 만든다(#797). RestClient는 이 구현 안에서만 쓰고 빈으로 등록하지 않는다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SearchEmbeddingProperties.class)
public class SearchEmbeddingConfig {

    // 검색 요청이 임베딩 API를 기다리므로 짧게 둔다. 넘으면 키워드로만 찾는다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    // 연속 실패 차단이 열려 있으면 1(#1026). 오래 열려 있으면 키 만료 · 한도 초과를 의심한다. 의미 검색을 끈 환경은 늘 0이다
    static final String CIRCUIT_OPEN_METRIC = "kidly.search.embedding.circuit.open";

    @Bean
    public TextEmbedder searchTextEmbedder(SearchEmbeddingProperties properties, MeterRegistry meterRegistry) {
        if (!properties.enabled()) {
            log.info("임베딩 API 키가 없어 의미 검색을 끕니다. 관련도순은 키워드로만 찾습니다");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        OpenAiTextEmbedder embedder = new OpenAiTextEmbedder(properties,
            RestClient.builder().requestFactory(requestFactory));
        Gauge.builder(CIRCUIT_OPEN_METRIC, embedder, it -> it.isCircuitOpen() ? 1 : 0)
            .description("임베딩 API 연속 실패 차단이 열려 있으면 1")
            .register(meterRegistry);
        return embedder;
    }
}
