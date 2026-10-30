package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchUnavailableException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 상품 색인에서 상품 목록을 찾고, 판매자 색인에서 상호명을 붙인다.
 * 조건은 모두 filter로 건다. 정렬이 점수가 아니라 정해진 필드라 점수를 계산할 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchProductSearch implements ProductSearch {

    // Elasticsearch가 from + size로 볼 수 있는 최대 건수(index.max_result_window 기본값). 넘으면 요청이 거절된다
    static final int MAX_RESULT_WINDOW = 10_000;

    private static final String ON_SALE = "ON_SALE";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public Page<ProductSearchItem> search(ProductSearchCondition condition, Pageable pageable) {
        SearchResponse<ProductDocument> response = searchProducts(condition, pageable);
        List<ProductDocument> documents = response.hits().hits().stream().map(Hit::source).toList();
        Map<Long, String> businessNames = findBusinessNames(documents);
        List<ProductSearchItem> content = documents.stream()
            .map(document -> new ProductSearchItem(
                document.productId(),
                document.name(),
                document.thumbnailUrl(),
                businessNames.get(document.sellerId()),
                document.minPrice(),
                document.soldOut(),
                document.sizeFree()))
            .toList();
        TotalHits total = response.hits().total();
        return new PageImpl<>(content, pageable, total == null ? 0 : total.value());
    }

    // 1만 건을 넘는 부분은 가져오지 않고 개수만 센다. 걸친 페이지는 1만 건까지만 가져온다.
    // 전체 개수는 1만 건을 넘어도 정확히 센다(track_total_hits). 응답의 totalElements · totalPages가 지금과 같게 하려는 것이다
    private SearchResponse<ProductDocument> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        long offset = pageable.getOffset();
        int size = (int) Math.max(0, Math.min(pageable.getPageSize(), MAX_RESULT_WINDOW - offset));
        int from = size == 0 ? 0 : (int) offset;
        SearchRequest request = SearchRequest.of(builder -> builder
            .index(properties.productIndex())
            .query(query -> query.bool(bool -> bool.filter(filters(condition))))
            .sort(sorts(condition.sort()))
            .from(from)
            .size(size)
            .trackTotalHits(track -> track.enabled(true)));
        try {
            return client.search(request, ProductDocument.class);
        } catch (IOException | ElasticsearchException e) {
            throw new SearchUnavailableException("Elasticsearch에서 상품을 찾지 못했습니다. index=" + properties.productIndex(), e);
        }
    }

    // 값은 모두 Elasticsearch 요청 객체에 값으로 넣으므로 사용자가 보낸 글자가 쿼리 문법이 되지 않는다
    private List<Query> filters(ProductSearchCondition condition) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(query -> query.term(term -> term.field("status").value(ON_SALE))));
        if (condition.categoryId() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("categoryId").value(condition.categoryId()))));
        }
        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            // 상품명과 같은 nori로 검색어를 형태소로 나누고, 모든 형태소가 상품명에 있어야 맞는다.
            // 띄어쓰기 · 조사 · 어순이 달라도 찾고(원피스 여아용 → 여아 원피스), 형태소 하나만 맞는 상품까지 넓히지 않는다
            filters.add(Query.of(query -> query.match(match -> match
                .field("name").query(condition.keyword().strip()).operator(Operator.And))));
        }
        for (Map.Entry<String, List<Long>> entry : condition.attributeValueIds().entrySet()) {
            List<Long> valueIds = entry.getValue();
            if (valueIds == null || valueIds.isEmpty()) {
                continue;
            }
            // 항목 하나에 terms 하나: 고른 값 중 하나만 맞아도 통과하고, 항목끼리는 filter라 모두 맞아야 한다
            List<FieldValue> values = valueIds.stream()
                .map(valueId -> FieldValue.of(entry.getKey() + ":" + valueId))
                .toList();
            filters.add(Query.of(query -> query.terms(terms -> terms
                .field("attributeValues").terms(field -> field.value(values)))));
        }
        return filters;
    }

    // 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다(같은 조건이면 페이지를 넘겨도 순서가 같다)
    private List<SortOptions> sorts(ProductSearchSort sort) {
        SortOptions first = switch (sort) {
            case NEWEST -> fieldSort("productCreatedAt", SortOrder.Desc);
            case PRICE_ASC -> fieldSort("minPrice", SortOrder.Asc);
            case PRICE_DESC -> fieldSort("minPrice", SortOrder.Desc);
        };
        return List.of(first, fieldSort("productId", SortOrder.Desc));
    }

    private static SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(sort -> sort.field(fieldSort -> fieldSort.field(field).order(order)));
    }

    // 판매자 문서가 없는 판매자는 Map에 넣지 않아 상호명이 null이 된다
    private Map<Long, String> findBusinessNames(List<ProductDocument> documents) {
        Set<String> sellerIds = new LinkedHashSet<>();
        for (ProductDocument document : documents) {
            sellerIds.add(String.valueOf(document.sellerId()));
        }
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        MgetResponse<SellerDocument> response;
        try {
            response = client.mget(MgetRequest.of(mget -> mget
                .index(properties.sellerIndex()).ids(List.copyOf(sellerIds))), SellerDocument.class);
        } catch (IOException | ElasticsearchException e) {
            throw new SearchUnavailableException("Elasticsearch에서 판매자를 찾지 못했습니다. index=" + properties.sellerIndex(), e);
        }
        Map<Long, String> businessNames = new HashMap<>();
        for (MultiGetResponseItem<SellerDocument> item : response.docs()) {
            if (item.isResult() && item.result().found() && item.result().source() != null) {
                SellerDocument seller = item.result().source();
                businessNames.put(seller.sellerId(), seller.businessName());
            }
        }
        return businessNames;
    }
}
