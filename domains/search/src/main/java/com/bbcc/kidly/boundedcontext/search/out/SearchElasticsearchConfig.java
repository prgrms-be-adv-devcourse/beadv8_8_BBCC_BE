package com.bbcc.kidly.boundedcontext.search.out;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 클라이언트(ElasticsearchClient)는 Boot가 spring.elasticsearch 설정으로 만든다. 여기서는 색인 이름 설정만 등록한다.
 */
@Configuration
@EnableConfigurationProperties(SearchIndexProperties.class)
public class SearchElasticsearchConfig {
}
