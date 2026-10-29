package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기동할 때 상품 · 판매자 색인이 없으면 resources/elasticsearch의 설정대로 만든다.
 * 설정 없이 첫 문서가 들어가면 Elasticsearch가 필드 타입을 짐작해 색인을 만들므로(한글 분석기 없음) 먼저 만들어 둔다.
 * Kafka 수신이 시작되기 전에 끝나도록 빈을 만들 때 실행한다. Elasticsearch에 연결하지 못하면 기동에 실패한다.
 * 이미 있는 색인은 설정이 달라도 바꾸지 않는다. 필드를 바꾸면 새 색인을 만들어 다시 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer {

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @PostConstruct
    public void createMissingIndexes() {
        createIfMissing(properties.productIndex(), "elasticsearch/products.json");
        createIfMissing(properties.sellerIndex(), "elasticsearch/sellers.json");
    }

    private void createIfMissing(String index, String settingsPath) {
        try {
            if (client.indices().exists(ExistsRequest.of(builder -> builder.index(index))).value()) {
                return;
            }
            try (InputStream settings = getClass().getClassLoader().getResourceAsStream(settingsPath)) {
                if (settings == null) {
                    throw new IllegalStateException("색인 설정 파일이 없습니다. path=" + settingsPath);
                }
                client.indices().create(CreateIndexRequest.of(builder -> builder.index(index).withJson(settings)));
            }
            log.info("검색 색인을 만들었습니다. index={}", index);
        } catch (IOException e) {
            throw new UncheckedIOException("검색 색인을 만들지 못했습니다. index=" + index, e);
        }
    }
}
