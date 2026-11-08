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
 * 관련도순(RELEVANCE)으로 검색어를 찾을 때만 점수를 쓴다(#797). 검색어가 상품명 · 브랜드에 있는 상품은 고정 점수
 * KEYWORD_SCORE를 받고, 검색어 벡터가 있으면 kNN으로 뜻이 가까운 상품을 더한다(kNN 점수는 코사인 유사도로 0.5~1).
 * 두 점수를 더해 높은 순으로 보여 주므로 검색어가 있는 상품이 늘 먼저 오고, 그 안에서는 뜻이 가까운 순이다.
 * BM25 점수를 쓰지 않는 건 상한이 없어 상품 수에 따라 kNN 점수와 앞뒤가 바뀌기 때문이다.
 * 검색어 형태소가 모두 상품명에 있는 상품은 고정 점수 NAME_SCORE를 더 받아, 브랜드 · 동의어로만 맞은 상품보다 먼저 온다(#1025).
 * 다른 정렬(최신순 · 가격순)도 검색어 벡터가 있으면 검색어가 있는 상품에 뜻이 가까운 상품을 더하고, 정한 필드로 정렬한다.
 * 점수를 쓰지 않으므로 kNN을 검색어 조건과 나란히 둔 should(하나만 맞으면 통과)로 건다.
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchProductSearch implements ProductSearch {

    // Elasticsearch가 from + size로 볼 수 있는 최대 건수(index.max_result_window 기본값). 넘으면 요청이 거절된다
    static final int MAX_RESULT_WINDOW = 10_000;

    private static final String ON_SALE = "ON_SALE";
    private static final int SERVER_ERROR = 500;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int NOT_FOUND = 404;
    private static final String INDEX_NOT_FOUND = "index_not_found_exception";

    // 의미 검색으로 더하는 상품 수와, 그 상품을 고르려고 살펴볼 후보 수다. 그보다 뒤 페이지는 키워드로 찾은 상품만 나온다
    static final int KNN_K = 100;
    static final int KNN_NUM_CANDIDATES = 200;
    static final String EMBEDDING = "embedding";
    // kNN 점수(최대 1)보다 커야 검색어가 있는 상품이 뜻만 가까운 상품보다 먼저 온다
    static final float KEYWORD_SCORE = 2.0f;
    // 검색어 형태소가 모두 상품명에 있을 때 더하는 점수. kNN 점수(최대 1)보다 커야 상품명이 맞은 상품이 늘 먼저 온다
    static final float NAME_SCORE = 2.0f;

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;
    private final SearchEmbeddingProperties embeddingProperties;
    private final SearchSynonyms synonyms;

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
        boolean byRelevance = condition.sort() == ProductSearchSort.RELEVANCE && condition.hasKeyword();
        SearchRequest request = SearchRequest.of(builder -> {
            builder.index(properties.productIndex())
                .from(from)
                .size(size)
                .trackTotalHits(track -> track.enabled(true));
            if (!byRelevance) {
                return builder
                    .query(sortedQuery(condition))
                    .sort(sorts(condition.sort()));
            }
            List<Query> filters = filters(condition, false);
            String keyword = condition.keyword().strip();
            builder.query(query -> query.bool(bool -> bool
                    .filter(filters)
                    .must(must -> must.constantScore(constant -> constant
                        .filter(keywordFilter(keyword))
                        .boost(KEYWORD_SCORE)))
                    .should(should -> should.constantScore(constant -> constant
                        .filter(matchAll("name", keyword))
                        .boost(NAME_SCORE)))))
                .sort(scoreSort(), fieldSort("productId", SortOrder.Desc));
            if (condition.keywordVector() != null) {
                builder.knn(knn -> knn
                    .field(EMBEDDING)
                    .queryVector(condition.keywordVector())
                    .k(KNN_K)
                    .numCandidates(KNN_NUM_CANDIDATES)
                    .similarity(embeddingProperties.minSimilarity())
                    .filter(filters));
            }
            return builder;
        });
        try {
            return client.search(request, ProductDocument.class);
        } catch (IOException e) {
            throw unavailable("상품", properties.productIndex(), e);
        } catch (ElasticsearchException e) {
            throw unavailableOrRethrow("상품", properties.productIndex(), e);
        }
    }

    // 검색어와 벡터가 모두 있으면 검색어가 상품명 · 브랜드에 있거나 뜻이 가까운 상품을 찾는다. 그 밖은 조건을 모두 filter로 건다
    private Query sortedQuery(ProductSearchCondition condition) {
        if (!condition.hasKeyword() || condition.keywordVector() == null) {
            return Query.of(query -> query.bool(bool -> bool.filter(filters(condition, true))));
        }
        List<Query> filters = filters(condition, false);
        Query semantic = Query.of(query -> query.knn(knn -> knn
            .field(EMBEDDING)
            .queryVector(condition.keywordVector())
            .k(KNN_K)
            .numCandidates(KNN_NUM_CANDIDATES)
            .similarity(embeddingProperties.minSimilarity())
            .filter(filters)));
        return Query.of(query -> query.bool(bool -> bool
            .filter(filters)
            .should(keywordFilter(condition.keyword().strip()), semantic)
            .minimumShouldMatch("1")));
    }

    // 값은 모두 Elasticsearch 요청 객체에 값으로 넣으므로 사용자가 보낸 글자가 쿼리 문법이 되지 않는다.
    // 관련도순은 검색어를 점수 조건으로 따로 걸므로 withKeyword=false로 검색어를 빼고 만든다(kNN에도 같은 조건을 건다)
    private List<Query> filters(ProductSearchCondition condition, boolean withKeyword) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(query -> query.term(term -> term.field("status").value(ON_SALE))));
        if (condition.categoryId() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("categoryId").value(condition.categoryId()))));
        }
        if (withKeyword && condition.hasKeyword()) {
            filters.add(keywordFilter(condition.keyword().strip()));
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

    // 검색어는 상품명이나 브랜드에서 찾는다. 둘 중 하나만 맞으면 된다.
    // 1) 상품명: 검색어 전체를 nori 형태소로 나눠 모든 형태소가 상품명에 있어야 맞는다. 띄어쓰기 · 조사 · 어순이 달라도 찾고
    //    (원피스 여아용 → 여아 원피스), 형태소 하나만 맞는 상품까지 넓히지 않는다
    // 2) 단어별: 공백으로 나눈 단어마다 상품명(nori)이나 브랜드(단어 그대로) 중 한 곳에 있어야 맞는다(키들리 원피스).
    //    브랜드는 고유명사라 nori가 앞뒤 글자에 따라 다르게 나누므로(키들리 → 키 · 들리, 키들리 원피스 → 키 · 리 · 원피스)
    //    형태소로 나누지 않고 단어로 맞춘다. 그래서 브랜드는 단어를 다 써야 찾는다(꼬마 → 꼬마숲은 못 찾음)
    // 3) 동의어(#1025): 단어별로 찾을 때 사전에서 넓힌 말(바지 → 팬츠)이 상품명에 있어도 맞는다. 검색어 전체(1)는 넓히지 않는다
    // 관련도순이 아니면 filter라 점수를 계산하지 않으므로 필드 가중치는 두지 않는다(관련도순의 상품명 점수는 따로 건다)
    private Query keywordFilter(String keyword) {
        List<Query> words = new ArrayList<>();
        for (String word : keyword.split("\\s+")) {
            List<Query> either = new ArrayList<>();
            either.add(matchAll("name", word));
            either.add(matchAll("brand", word));
            for (String synonym : synonyms.expand(word)) {
                either.add(matchAll("name", synonym));
            }
            words.add(Query.of(query -> query.bool(bool -> bool.should(either).minimumShouldMatch("1"))));
        }
        return Query.of(query -> query.bool(bool -> bool
            .should(matchAll("name", keyword), Query.of(byWord -> byWord.bool(all -> all.filter(words))))
            .minimumShouldMatch("1")));
    }

    private static Query matchAll(String field, String text) {
        return Query.of(query -> query.match(match -> match.field(field).query(text).operator(Operator.And)));
    }

    // 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다(같은 조건이면 페이지를 넘겨도 순서가 같다).
    // 검색어 없는 관련도순은 점수가 모두 같아 최신순으로 보여 준다
    private List<SortOptions> sorts(ProductSearchSort sort) {
        SortOptions first = switch (sort) {
            case NEWEST, RELEVANCE -> fieldSort("productCreatedAt", SortOrder.Desc);
            case PRICE_ASC -> fieldSort("minPrice", SortOrder.Asc);
            case PRICE_DESC -> fieldSort("minPrice", SortOrder.Desc);
        };
        return List.of(first, fieldSort("productId", SortOrder.Desc));
    }

    private static SortOptions scoreSort() {
        return SortOptions.of(sort -> sort.score(score -> score.order(SortOrder.Desc)));
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
        } catch (IOException e) {
            throw unavailable("판매자", properties.sellerIndex(), e);
        } catch (ElasticsearchException e) {
            throw unavailableOrRethrow("판매자", properties.sellerIndex(), e);
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

    private static SearchUnavailableException unavailable(String target, String index, IOException e) {
        return new SearchUnavailableException(
            "Elasticsearch에 연결하지 못해 " + target + "을(를) 찾지 못했습니다. index=" + index, e);
    }

    // Elasticsearch 쪽 장애(5xx · 과부하 429 · 색인 없음)만 검색 불가로 바꾼다. 다시 하면 될 수 있어 503으로 알린다.
    // 그 밖의 4xx는 요청을 잘못 만든 우리 쪽 버그라 그대로 던진다(500, 스택 트레이스와 함께 ERROR). 503으로 가리면 원인을 찾기 어렵다
    private static RuntimeException unavailableOrRethrow(String target, String index, ElasticsearchException e) {
        int status = e.status();
        boolean indexMissing = status == NOT_FOUND && INDEX_NOT_FOUND.equals(e.error().type());
        if (status >= SERVER_ERROR || status == TOO_MANY_REQUESTS || indexMissing) {
            return new SearchUnavailableException(
                "Elasticsearch가 " + target + " 조회를 처리하지 못했습니다. index=" + index + " status=" + status, e);
        }
        return e;
    }
}
