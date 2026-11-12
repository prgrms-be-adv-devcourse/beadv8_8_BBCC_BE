package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchProperties;
import org.springframework.boot.elasticsearch.autoconfigure.Rest5ClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 클라이언트(ElasticsearchClient)는 Boot가 spring.elasticsearch 설정으로 만든다.
 * 여기서는 색인 이름 설정, 검색할 때 쓰는 동의어 사전(#1025), 요청 응답 시간 제한(#1026)을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(SearchIndexProperties.class)
public class SearchElasticsearchConfig {

    @Bean
    public SearchSynonyms searchSynonyms() {
        return SearchSynonyms.load(SearchSynonyms.DEFAULT_PATH);
    }

    /**
     * Boot는 spring.elasticsearch.socket-timeout을 연결 설정에만 넣고, Elasticsearch 클라이언트(Rest5Client)는 요청마다
     * 자기 기본 응답 시간 제한(30초)을 건다. 그래서 설정한 값이 요청에 먹지 않아 요청 설정에도 같은 값을 넣는다.
     * 연결 풀에서 연결을 기다리는 시간도 같게 둔다. Elasticsearch가 느려 연결이 모두 묶여도 그만큼만 기다린다.
     */
    @Bean
    public Rest5ClientBuilderCustomizer searchElasticsearchTimeoutCustomizer(ElasticsearchProperties properties) {
        Timeout timeout = Timeout.of(properties.getSocketTimeout());
        return new Rest5ClientBuilderCustomizer() {
            @Override
            public void customize(Rest5ClientBuilder builder) {
            }

            @Override
            public void customize(RequestConfig.Builder builder) {
                builder.setResponseTimeout(timeout).setConnectionRequestTimeout(timeout);
            }
        };
    }
}
