package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기동할 때 상품 · 판매자 색인이 없으면 resources/elasticsearch의 설정대로 만들고, 있으면 설정 파일에 새로 더한 필드를 넣는다.
 * 설정 없이 첫 문서가 들어가면 Elasticsearch가 필드 타입을 짐작해 색인을 만들므로(한글 분석기 없음) 먼저 만들어 둔다.
 * Kafka 수신이 시작되기 전에 끝나도록 빈을 만들 때 실행한다. Elasticsearch에 연결하지 못하면 기동에 실패한다.
 * 이미 있는 색인에는 필드를 더하기만 한다(dynamic: strict라 필드가 없으면 그 필드를 담은 문서 저장이 거절된다).
 * 있는 필드의 타입 · 분석기를 바꾸면 Elasticsearch가 거절해 기동에 실패한다. 그때는 새 색인을 만들어 다시 채운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer {

    private static final String ALREADY_EXISTS = "resource_already_exists_exception";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @PostConstruct
    public void createOrUpdateIndexes() {
        createOrUpdate(properties.productIndex(), "elasticsearch/products.json");
        createOrUpdate(properties.sellerIndex(), "elasticsearch/sellers.json");
    }

    private void createOrUpdate(String index, String settingsPath) {
        try {
            CreateIndexRequest create = readSettings(index, settingsPath);
            if (client.indices().exists(ExistsRequest.of(builder -> builder.index(index))).value()) {
                addNewFields(index, create.mappings());
                return;
            }
            client.indices().create(create);
            log.info("검색 색인을 만들었습니다. index={}", index);
        } catch (ElasticsearchException e) {
            // 확인과 생성 사이에 다른 인스턴스가 먼저 만들었으면 이미 있는 것이므로 그대로 기동한다
            if (!ALREADY_EXISTS.equals(e.error().type())) {
                throw e;
            }
            log.info("다른 인스턴스가 먼저 검색 색인을 만들었습니다. index={}", index);
        } catch (IOException e) {
            throw new UncheckedIOException("검색 색인을 만들지 못했습니다. index=" + index, e);
        }
    }

    private CreateIndexRequest readSettings(String index, String settingsPath) throws IOException {
        try (InputStream settings = getClass().getClassLoader().getResourceAsStream(settingsPath)) {
            if (settings == null) {
                throw new IllegalStateException("색인 설정 파일이 없습니다. path=" + settingsPath);
            }
            return CreateIndexRequest.of(builder -> builder.index(index).withJson(settings));
        }
    }

    // 설정 파일의 필드를 모두 보낸다. 이미 있는 필드는 같은 값이라 그대로이고, 없는 필드만 더해진다.
    // 새 필드는 그 뒤에 색인한 문서부터 채워지므로, 이미 있는 문서는 보내는 쪽의 전체 다시 발행으로 채운다
    private void addNewFields(String index, TypeMapping mappings) throws IOException {
        client.indices().putMapping(PutMappingRequest.of(builder -> builder
            .index(index).dynamic(mappings.dynamic()).properties(mappings.properties())));
        log.info("검색 색인에 설정 파일의 필드를 맞췄습니다. index={}", index);
    }
}
