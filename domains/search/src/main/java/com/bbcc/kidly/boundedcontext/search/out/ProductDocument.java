package com.bbcc.kidly.boundedcontext.search.out;

import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
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
 */
public record ProductDocument(
    Long productId,
    String name,
    String thumbnailUrl,
    Long sellerId,
    Long categoryId,
    String status,
    boolean sizeFree,
    String productCreatedAt,
    long minPrice,
    boolean soldOut,
    List<String> attributeValues,
    String occurredAt) {

    static ProductDocument from(SearchProduct product) {
        return new ProductDocument(
            product.getProductId(),
            product.getName(),
            product.getThumbnailUrl(),
            product.getSellerId(),
            product.getCategoryId(),
            product.getStatus().name(),
            product.isSizeFree(),
            product.getProductCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            product.getMinPrice(),
            product.isSoldOut(),
            toAttributeValues(product.getAttributes()),
            product.getOccurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
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
