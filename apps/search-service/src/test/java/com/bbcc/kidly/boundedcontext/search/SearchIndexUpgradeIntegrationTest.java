package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchEvents.product;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexInitializer;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * 브랜드 필드가 없던 예전 상품 색인이 남아 있는 환경(dev)에서 다시 기동하면, 색인을 새로 만들지 않고 브랜드 필드를 더해
 * 브랜드가 담긴 문서를 저장하고 찾을 수 있는지 실제 Elasticsearch로 확인한다(#610).
 */
@IntegrationTest
class SearchIndexUpgradeIntegrationTest {

    private static final String BRAND_FIELD = "      \"brand\": { \"type\": \"text\", \"analyzer\": \"standard\" },\n";
    private static final String EMBEDDING_FIELDS = ",\n"
        + "      \"embedding\": { \"type\": \"dense_vector\", \"dims\": 512, \"similarity\": \"cosine\", "
        + "\"index\": true, \"index_options\": { \"type\": \"int8_hnsw\" } },\n"
        + "      \"embeddingTextHash\": { \"type\": \"keyword\", \"index\": false }";

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private SearchIndexProperties properties;

    @Autowired
    private SearchIndexInitializer initializer;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private SearchIndexes indexes;

    @Test
    @DisplayName("브랜드 필드가 없는 예전 색인에 기동할 때 브랜드 필드를 더하고, 있던 문서는 그대로 찾는다")
    void addsBrandFieldToOldIndex() throws IOException {
        givenOldProductIndex();
        givenOldDocument(1L, "여아 원피스");

        initializer.createOrUpdateIndexes();
        indexService.applyProductChanged(product(2L, "봄 원피스", "키들리", 7L, 3L, ProductStatus.ON_SALE, 18_000, 3L, 2,
            SearchEvents.OCCURRED_AT));
        indexes.refresh();

        assertThat(client.indices().getMapping(get -> get.index(properties.productIndex()))
            .get(properties.productIndex()).mappings().properties().get("brand").text().analyzer())
            .isEqualTo("standard");
        assertThat(ids("키들리")).containsExactly(2L);
        assertThat(ids("원피스")).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("의미 검색 벡터 필드가 없는 예전 색인에 기동할 때 벡터 필드를 더하고, 있던 문서는 그대로 찾는다(#797)")
    void addsEmbeddingFieldsToOldIndex() throws IOException {
        givenOldProductIndex(EMBEDDING_FIELDS);
        givenOldDocument(1L, "여아 원피스");

        initializer.createOrUpdateIndexes();
        indexes.refresh();

        var fields = client.indices().getMapping(get -> get.index(properties.productIndex()))
            .get(properties.productIndex()).mappings().properties();
        assertThat(fields.get("embedding").denseVector().dims()).isEqualTo(512);
        assertThat(fields.get("embeddingTextHash").isKeyword()).isTrue();
        assertThat(ids("원피스")).containsExactly(1L);
    }

    // 지금 설정 파일에서 브랜드 필드만 뺀 예전 설정으로 상품 색인을 다시 만든다
    private void givenOldProductIndex() throws IOException {
        givenOldProductIndex(BRAND_FIELD);
    }

    // 지금 설정 파일에서 주어진 필드 정의를 뺀 예전 설정으로 상품 색인을 다시 만든다
    private void givenOldProductIndex(String removedFields) throws IOException {
        String current;
        try (InputStream settings = getClass().getClassLoader().getResourceAsStream("elasticsearch/products.json")) {
            current = new String(settings.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(current).contains(removedFields);
        String old = current.replace(removedFields, "");
        client.indices().delete(delete -> delete.index(properties.productIndex()).ignoreUnavailable(true));
        client.indices().create(CreateIndexRequest.of(create -> create
            .index(properties.productIndex()).withJson(new StringReader(old))));
    }

    // 예전 코드가 저장한 것처럼 브랜드 칸 없이 문서를 넣는다
    private void givenOldDocument(long productId, String name) throws IOException {
        Map<String, Object> document = new HashMap<>();
        document.put("productId", productId);
        document.put("name", name);
        document.put("sellerId", 7L);
        document.put("categoryId", 3L);
        document.put("status", "ON_SALE");
        document.put("sizeFree", false);
        document.put("productCreatedAt", "2026-10-01T09:00:00");
        document.put("minPrice", 15_000L);
        document.put("soldOut", false);
        document.put("attributeValues", List.of("COLOR:3"));
        document.put("occurredAt", "2026-10-15T10:00:00");
        client.index(index -> index.index(properties.productIndex()).id(String.valueOf(productId)).document(document));
    }

    private List<Long> ids(String keyword) {
        return productSearch.search(new ProductSearchCondition(null, keyword, Map.of(), ProductSearchSort.NEWEST),
                PageRequest.of(0, 20)).getContent().stream()
            .map(ProductSearchItem::productId).toList();
    }
}
