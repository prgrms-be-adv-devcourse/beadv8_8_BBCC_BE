package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchUnavailableException;
import com.bbcc.kidly.boundedcontext.search.out.ElasticsearchProductSearch;
import com.bbcc.kidly.boundedcontext.search.out.ProductDocument;
import com.bbcc.kidly.boundedcontext.search.out.SearchIndexProperties;
import com.bbcc.kidly.boundedcontext.search.out.SellerDocument;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 상품 목록 조건을 Elasticsearch 검색 요청으로 바꾸고, 결과에 판매자 상호명을 붙이는지 확인한다.
 * 계약(판매중만 · 거르기 · 정렬 · 페이지)은 상품의 PostgresProductSearch와 같다. 실제 형태소 검색은 search-service 통합 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchTest {

    @Mock
    private ElasticsearchClient client;

    @Captor
    private ArgumentCaptor<SearchRequest> searchCaptor;

    @Captor
    private ArgumentCaptor<MgetRequest> mgetCaptor;

    private ElasticsearchProductSearch productSearch;

    @BeforeEach
    void setUp() {
        productSearch = new ElasticsearchProductSearch(client, new SearchIndexProperties("local"));
    }

    @Test
    @DisplayName("조건이 없으면 환경별 상품 색인에서 판매중 상품만 최신순으로 찾는다")
    void onSaleOnlyByDefault() throws IOException {
        givenProducts(0);

        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        SearchRequest request = capturedSearch();
        assertThat(request.index()).containsExactly("local-products");
        assertThat(filters(request)).singleElement().satisfies(filter -> {
            assertThat(filter.term().field()).isEqualTo("status");
            assertThat(filter.term().value().stringValue()).isEqualTo("ON_SALE");
        });
        assertThat(request.trackTotalHits().enabled()).isTrue();
        assertThat(request.from()).isZero();
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("카테고리 · 검색어로 거른다. 검색어는 상품명을 형태소로 나눠 모든 형태소가 맞아야 한다")
    void categoryAndKeyword() throws IOException {
        givenProducts(0);

        productSearch.search(condition(3L, "원피스 여아용", Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        List<Query> filters = filters(capturedSearch());
        assertThat(filters).filteredOn(Query::isTerm).anySatisfy(filter -> {
            assertThat(filter.term().field()).isEqualTo("categoryId");
            assertThat(filter.term().value().longValue()).isEqualTo(3L);
        });
        assertThat(filters).filteredOn(Query::isMatch).singleElement().satisfies(filter -> {
            assertThat(filter.match().field()).isEqualTo("name");
            assertThat(filter.match().query().stringValue()).isEqualTo("원피스 여아용");
            assertThat(filter.match().operator()).isEqualTo(Operator.And);
        });
    }

    @Test
    @DisplayName("검색어가 공백뿐이면 검색어로 거르지 않는다")
    void blankKeyword() throws IOException {
        givenProducts(0);

        productSearch.search(condition(null, "  ", Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        assertThat(filters(capturedSearch())).noneMatch(Query::isMatch);
    }

    @Test
    @DisplayName("항목마다 고른 값 중 하나만 맞으면 되고, 항목이 다르면 모두 맞아야 한다. 값이 빈 항목은 쓰지 않는다")
    void attributes() throws IOException {
        givenProducts(0);
        Map<String, List<Long>> attributes = new LinkedHashMap<>();
        attributes.put("COLOR", List.of(3L, 5L));
        attributes.put("SIZE", List.of(11L));
        attributes.put("MATERIAL", List.of());

        productSearch.search(condition(null, null, attributes, ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        List<List<String>> termsFilters = filters(capturedSearch()).stream()
            .filter(Query::isTerms)
            .map(filter -> {
                assertThat(filter.terms().field()).isEqualTo("attributeValues");
                return filter.terms().terms().value().stream().map(FieldValue::stringValue).toList();
            })
            .toList();
        assertThat(termsFilters).containsExactly(List.of("COLOR:3", "COLOR:5"), List.of("SIZE:11"));
    }

    @Test
    @DisplayName("정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다")
    void sorts() throws IOException {
        givenProducts(0);

        for (ProductSearchSort sort : ProductSearchSort.values()) {
            productSearch.search(condition(null, null, Map.of(), sort), PageRequest.of(0, 20));
        }

        verify(client, times(3)).search(searchCaptor.capture(), eq(ProductDocument.class));
        assertThat(searchCaptor.getAllValues()).extracting(request -> sortKeys(request.sort())).containsExactly(
            List.of("productCreatedAt desc", "productId desc"),
            List.of("minPrice asc", "productId desc"),
            List.of("minPrice desc", "productId desc"));
    }

    @Test
    @DisplayName("결과에 판매자 색인의 상호명을 붙인다. 판매자 문서가 없으면 상호명은 null이다")
    void businessName() throws IOException {
        givenProducts(2, product(10L, 7L), product(11L, 8L));
        givenSellers(found("7", new SellerDocument(7L, "키들리 상점", "2026-10-15T10:00:00")), missing("8"));

        Page<ProductSearchItem> page = productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        verify(client).mget(mgetCaptor.capture(), eq(SellerDocument.class));
        assertThat(mgetCaptor.getValue().index()).isEqualTo("local-sellers");
        assertThat(mgetCaptor.getValue().ids()).containsExactly("7", "8");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).containsExactly(
            new ProductSearchItem(10L, "상품 10", "https://cdn.kidly.test/10.jpg", "키들리 상점", 15_000L, false, true),
            new ProductSearchItem(11L, "상품 11", "https://cdn.kidly.test/11.jpg", null, 15_000L, false, true));
    }

    @Test
    @DisplayName("같은 판매자의 상품이 여럿이어도 판매자는 한 번만 찾는다")
    void sameSellerOnce() throws IOException {
        givenProducts(2, product(10L, 7L), product(11L, 7L));
        givenSellers(found("7", new SellerDocument(7L, "키들리 상점", "2026-10-15T10:00:00")));

        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        verify(client).mget(mgetCaptor.capture(), eq(SellerDocument.class));
        assertThat(mgetCaptor.getValue().ids()).containsExactly("7");
    }

    @Test
    @DisplayName("찾은 상품이 없으면 판매자 색인을 찾지 않는다")
    void noSellerLookupWhenEmpty() throws IOException {
        givenProducts(0);

        Page<ProductSearchItem> page = productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(client, never()).mget(any(MgetRequest.class), eq(SellerDocument.class));
    }

    @Test
    @DisplayName("페이지 번호와 크기로 건너뛸 개수와 가져올 개수를 정한다")
    void page() throws IOException {
        givenProducts(0);

        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(3, 20));

        SearchRequest request = capturedSearch();
        assertThat(request.from()).isEqualTo(60);
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("Elasticsearch가 한 번에 볼 수 있는 1만 건을 넘는 부분은 가져오지 않는다. 걸친 페이지는 1만 건까지만 가져온다")
    void resultWindow() throws IOException {
        givenProducts(0);

        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(499, 20));
        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(500, 20));
        productSearch.search(condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(476, 21));

        verify(client, times(3)).search(searchCaptor.capture(), eq(ProductDocument.class));
        assertThat(searchCaptor.getAllValues()).extracting(SearchRequest::from, SearchRequest::size)
            .containsExactly(
                tuple(9_980, 20),
                // 개수만 센다
                tuple(0, 0),
                tuple(9_996, 4));
    }

    @Test
    @DisplayName("1만 건 너머 페이지는 빈 목록과 전체 개수를 돌려준다")
    void beyondResultWindow() throws IOException {
        givenProducts(12_000);

        Page<ProductSearchItem> page = productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(600, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(12_000);
    }

    @Test
    @DisplayName("Elasticsearch에 연결하지 못하면 검색 색인을 쓸 수 없다고 알린다")
    void connectionFailure() throws IOException {
        when(client.search(any(SearchRequest.class), eq(ProductDocument.class)))
            .thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20)))
            .isInstanceOf(SearchUnavailableException.class)
            .hasMessageContaining("local-products");
    }

    @Test
    @DisplayName("Elasticsearch가 오류로 답하면(색인 없음 · 과부하 등) 검색 색인을 쓸 수 없다고 알린다")
    void elasticsearchError() throws IOException {
        when(client.search(any(SearchRequest.class), eq(ProductDocument.class)))
            .thenThrow(ElasticsearchSearchProductRepositoryTest.errorWithStatus(503));

        assertThatThrownBy(() -> productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20)))
            .isInstanceOf(SearchUnavailableException.class);
    }

    @Test
    @DisplayName("판매자 색인을 쓸 수 없어도 검색 색인을 쓸 수 없다고 알린다")
    void sellerLookupFailure() throws IOException {
        givenProducts(1, product(10L, 7L));
        when(client.mget(any(MgetRequest.class), eq(SellerDocument.class)))
            .thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20)))
            .isInstanceOf(SearchUnavailableException.class)
            .hasMessageContaining("local-sellers");
    }

    private static ProductSearchCondition condition(Long categoryId, String keyword,
        Map<String, List<Long>> attributes, ProductSearchSort sort) {
        return new ProductSearchCondition(categoryId, keyword, attributes, sort);
    }

    private void givenProducts(long total, ProductDocument... documents) throws IOException {
        List<Hit<ProductDocument>> hits = new ArrayList<>();
        for (ProductDocument document : documents) {
            hits.add(Hit.of(hit -> hit.index("local-products").id(String.valueOf(document.productId()))
                .source(document)));
        }
        SearchResponse<ProductDocument> response = SearchResponse.of(builder -> builder
            .took(1)
            .timedOut(false)
            .shards(shards -> shards.total(1).successful(1).failed(0))
            .hits(result -> result.total(count -> count.value(total).relation(TotalHitsRelation.Eq)).hits(hits)));
        when(client.search(any(SearchRequest.class), eq(ProductDocument.class))).thenReturn(response);
    }

    @SafeVarargs
    private void givenSellers(MultiGetResponseItem<SellerDocument>... items) throws IOException {
        when(client.mget(any(MgetRequest.class), eq(SellerDocument.class)))
            .thenReturn(MgetResponse.of(response -> response.docs(List.of(items))));
    }

    private SearchRequest capturedSearch() throws IOException {
        verify(client).search(searchCaptor.capture(), eq(ProductDocument.class));
        return searchCaptor.getValue();
    }

    private static List<Query> filters(SearchRequest request) {
        assertThat(request.query().bool().must()).isEmpty();
        return request.query().bool().filter();
    }

    private static List<String> sortKeys(List<SortOptions> sorts) {
        return sorts.stream()
            .map(sort -> sort.field().field() + " " + (sort.field().order() == SortOrder.Asc ? "asc" : "desc"))
            .toList();
    }

    private static ProductDocument product(Long productId, Long sellerId) {
        return new ProductDocument(productId, "상품 " + productId, "https://cdn.kidly.test/" + productId + ".jpg",
            sellerId, 3L, "ON_SALE", true, "2026-10-01T09:00:00", 15_000L, false, List.of(),
            "2026-10-15T10:00:00");
    }

    private static MultiGetResponseItem<SellerDocument> found(String id, SellerDocument document) {
        return MultiGetResponseItem.of(item -> item.result(result -> result
            .index("local-sellers").id(id).found(true).source(document)));
    }

    private static MultiGetResponseItem<SellerDocument> missing(String id) {
        return MultiGetResponseItem.of(item -> item.result(result -> result
            .index("local-sellers").id(id).found(false)));
    }
}
