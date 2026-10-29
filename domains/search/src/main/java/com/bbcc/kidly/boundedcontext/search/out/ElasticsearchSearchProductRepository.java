package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 검색 문서를 Elasticsearch 상품 색인에 저장한다. 늦은 이벤트는 외부 버전 비교로 거른다(VersionedIndexing).
 */
@Repository
@RequiredArgsConstructor
public class ElasticsearchSearchProductRepository implements SearchProductRepository {

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public void saveIfNewer(SearchProduct product) {
        VersionedIndexing.indexIfNewer(client, properties.productIndex(), String.valueOf(product.getProductId()),
            product.getOccurredAt(), ProductDocument.from(product));
    }
}
