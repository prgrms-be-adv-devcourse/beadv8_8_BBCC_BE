package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 검색 색인 상태 지표(#1026). 벡터 없이 저장된 판매중 상품 수를 Prometheus가 읽을 때마다 센다.
 * 임베딩 API를 쓸 수 없던 동안 색인된 상품은 벡터 없이 저장되어, 채워지기 전까지 의미 검색에 나오지 않는다.
 * 검색이 1분마다 저장해 둔 문장으로 다시 만들므로(SearchEmbeddingRefillScheduler) API가 돌아오면 줄어든다.
 * 줄지 않고 멈춰 있으면 문장 없이 저장된 예전 문서이므로 상품을 다시 발행한다(deploy/search/README.md).
 * 의미 검색을 끈 환경(키 없음)은 모든 상품에 벡터가 없으므로 등록하지 않는다.
 * 셀 수 없으면(Elasticsearch 장애 · 시간 초과) NaN을 돌려주고 DEBUG만 남긴다(15초마다 읽으므로).
 * 장애는 검색 503 · Kafka DLT로 따로 보인다.
 */
@Slf4j
@Component
public class SearchIndexMetrics {

    static final String WITHOUT_EMBEDDING_METRIC = "kidly.search.products.without.embedding";
    private static final String ON_SALE = "ON_SALE";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    public SearchIndexMetrics(ElasticsearchClient client, SearchIndexProperties properties,
        SearchEmbeddingProperties embeddingProperties, MeterRegistry meterRegistry) {
        this.client = client;
        this.properties = properties;
        if (embeddingProperties.enabled()) {
            Gauge.builder(WITHOUT_EMBEDDING_METRIC, this, SearchIndexMetrics::countOnSaleWithoutEmbedding)
                .description("의미 검색 벡터 없이 저장된 판매중 상품 수")
                .register(meterRegistry);
        }
    }

    double countOnSaleWithoutEmbedding() {
        String index = properties.productIndex();
        try {
            return client.count(count -> count
                .index(index)
                .query(query -> query.bool(bool -> bool
                    .filter(Query.of(onSale -> onSale.term(term -> term.field("status").value(ON_SALE))))
                    .mustNot(Query.of(vector -> vector.exists(exists -> exists.field("embedding")))))))
                .count();
        } catch (IOException | ElasticsearchException e) {
            log.debug("벡터 없는 상품 수를 세지 못했습니다. index={} exception={}", index, e.getClass().getName());
            return Double.NaN;
        }
    }
}
