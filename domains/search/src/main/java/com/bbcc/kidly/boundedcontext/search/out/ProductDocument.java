package com.bbcc.kidly.boundedcontext.search.out;

import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Elasticsearch 상품 색인에 저장하는 문서다. 필드 타입은 resources/elasticsearch/products.json에 정해 둔다.
 *
 * @param attributeValues 항목 code와 값 ID를 code:값ID 한 단어로 묶은 목록(예: color:3). 항목이 늘어도 필드가
 *     늘지 않게 keyword 필드 하나에 담고, 항목마다 고른 값 중 하나만 맞아도 통과하는 조건으로 거른다
 * @param embedding 의미 검색 벡터(#797). 없으면 칸을 보내지 않는다(dense_vector에 null을 넣지 않는다).
 *     Elasticsearch 9.2부터 검색 · 조회 응답의 _source에 벡터가 빠지므로 목록 검색 결과에서는 늘 null이다
 * @param embeddingTextHash 벡터를 만든 문장의 해시. 벡터가 없으면 보내지 않는다
 * @param embeddingText 벡터 없이 저장할 때만 담는 의미 검색 문장. 검색이 혼자 벡터를 다시 만드는 데 쓴다(색인 안 함).
 *     벡터가 있으면 보내지 않는다
 */
public record ProductDocument(
    Long productId,
    String name,
    String thumbnailUrl,
    String brand,
    Long sellerId,
    Long categoryId,
    String status,
    boolean sizeFree,
    String productCreatedAt,
    long minPrice,
    boolean soldOut,
    List<String> attributeValues,
    String occurredAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Float> embedding,
    @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingTextHash,
    @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingText) {

    // 벡터 없는 문서다(테스트 · 예전 문서와 같은 모양)
    public ProductDocument(Long productId, String name, String thumbnailUrl, String brand, Long sellerId,
        Long categoryId, String status, boolean sizeFree, String productCreatedAt, long minPrice, boolean soldOut,
        List<String> attributeValues, String occurredAt) {
        this(productId, name, thumbnailUrl, brand, sellerId, categoryId, status, sizeFree, productCreatedAt, minPrice,
            soldOut, attributeValues, occurredAt, null, null, null);
    }

    // 벡터를 붙이고, 다시 만들려고 둔 문장은 뺀다
    ProductDocument withEmbedding(ProductEmbedding embedding) {
        return new ProductDocument(productId, name, thumbnailUrl, brand, sellerId, categoryId, status, sizeFree,
            productCreatedAt, minPrice, soldOut, attributeValues, occurredAt, embedding.vector(), embedding.textHash(),
            null);
    }

    static ProductDocument from(SearchProduct product) {
        ProductEmbedding embedding = product.getEmbedding();
        return new ProductDocument(
            product.getProductId(),
            product.getName(),
            product.getThumbnailUrl(),
            product.getBrand(),
            product.getSellerId(),
            product.getCategoryId(),
            product.getStatus().name(),
            product.isSizeFree(),
            product.getProductCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            product.getMinPrice(),
            product.isSoldOut(),
            toAttributeValues(product.getAttributes()),
            product.getOccurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            embedding == null ? null : embedding.vector(),
            embedding == null ? null : embedding.textHash(),
            embedding == null ? product.getEmbeddingText() : null);
    }

    // 같은 상품이면 늘 같은 순서가 되게 항목 code 순, 값 ID 순(검색 문서가 이미 오름차순)으로 담는다
    private static List<String> toAttributeValues(Map<String, List<Long>> attributes) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : new TreeMap<>(attributes).entrySet()) {
            for (Long valueId : entry.getValue()) {
                values.add(entry.getKey() + ":" + valueId);
            }
        }
        return values;
    }
}
