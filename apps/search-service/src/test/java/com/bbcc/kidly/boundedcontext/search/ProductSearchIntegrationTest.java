package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchEvents.product;
import static com.bbcc.kidly.boundedcontext.search.SearchEvents.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.search.app.SearchIndexService;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 상품 · 판매자 이벤트로 색인한 문서를 실제 Elasticsearch(nori)에서 목록 조건으로 찾는다(#526).
 */
@IntegrationTest
class ProductSearchIntegrationTest {

    private static final long SHOP = 7L;
    private static final long NO_DOCUMENT_SELLER = 8L;
    private static final long DRESS = 3L;
    private static final long SHIRT = 4L;
    private static final long PINK = 3L;
    private static final long BLUE = 5L;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private SearchIndexes indexes;

    @BeforeEach
    void indexProducts() {
        indexService.applyProductChanged(product(1L, "여아 원피스", SHOP, DRESS, ProductStatus.ON_SALE, 15_000, PINK, 1));
        indexService.applyProductChanged(
            product(2L, "남아 셔츠", NO_DOCUMENT_SELLER, SHIRT, ProductStatus.ON_SALE, 9_000, BLUE, 2));
        indexService.applyProductChanged(
            product(3L, "여아 원피스 세트", SHOP, DRESS, ProductStatus.STOPPED, 1_000, PINK, 3));
        indexService.applyProductChanged(product(4L, "원피스", SHOP, DRESS, ProductStatus.ON_SALE, 20_000, BLUE, 4));
        indexService.applySellerChanged(seller(SHOP, "키들리 상점"));
        indexes.refresh();
    }

    @Test
    @DisplayName("띄어쓰기 · 접미사 · 어순이 달라도 형태소가 모두 맞으면 찾는다")
    void morphology() {
        assertThat(ids(keyword("원피스 여아용"))).containsExactly(1L);
        assertThat(ids(keyword("여아용 원피스"))).containsExactly(1L);
    }

    @Test
    @DisplayName("형태소 하나만 맞는 상품까지 넓히지 않는다")
    void allMorphemesMustMatch() {
        assertThat(ids(keyword("남아 원피스"))).isEmpty();
    }

    @Test
    @DisplayName("판매중 상품만 최신순으로 돌려준다")
    void onSaleOnly() {
        assertThat(ids(keyword("원피스"))).containsExactly(4L, 1L);
    }

    @Test
    @DisplayName("가격순으로 정렬한다")
    void priceSort() {
        assertThat(ids(condition(null, null, Map.of(), ProductSearchSort.PRICE_ASC))).containsExactly(2L, 1L, 4L);
        assertThat(ids(condition(null, null, Map.of(), ProductSearchSort.PRICE_DESC))).containsExactly(4L, 1L, 2L);
    }

    @Test
    @DisplayName("카테고리와 항목 값으로 거른다")
    void filters() {
        assertThat(ids(condition(DRESS, null, Map.of(), ProductSearchSort.NEWEST))).containsExactly(4L, 1L);
        assertThat(ids(condition(null, null, Map.of("COLOR", List.of(PINK)), ProductSearchSort.NEWEST)))
            .containsExactly(1L);
        assertThat(ids(condition(null, null, Map.of("COLOR", List.of(PINK, BLUE)), ProductSearchSort.NEWEST)))
            .containsExactly(4L, 2L, 1L);
    }

    @Test
    @DisplayName("판매자 상호명을 붙인다. 판매자 문서가 없으면 null이다. 전체 개수와 페이지를 돌려준다")
    void businessNameAndPage() {
        Page<ProductSearchItem> page = productSearch.search(
            condition(null, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ProductSearchItem::productId, ProductSearchItem::businessName)
            .containsExactly(tuple(4L, "키들리 상점"),
                tuple(2L, null));
    }

    @Test
    @DisplayName("저장된 문서보다 먼저 일어난 이벤트가 늦게 오면 무시한다")
    void ignoresLateEvent() {
        indexService.applyProductChanged(product(4L, "예전 이름", SHOP, DRESS, ProductStatus.ON_SALE, 20_000, BLUE, 4,
            SearchEvents.OCCURRED_AT.minusMinutes(1)));
        indexes.refresh();

        assertThat(productSearch.search(keyword("원피스"), PageRequest.of(0, 20)).getContent())
            .extracting(ProductSearchItem::name).contains("원피스").doesNotContain("예전 이름");
    }

    private static ProductSearchCondition keyword(String keyword) {
        return condition(null, keyword, Map.of(), ProductSearchSort.NEWEST);
    }

    private static ProductSearchCondition condition(Long categoryId, String keyword,
        Map<String, List<Long>> attributes, ProductSearchSort sort) {
        return new ProductSearchCondition(categoryId, keyword, attributes, sort);
    }

    private List<Long> ids(ProductSearchCondition condition) {
        return productSearch.search(condition, PageRequest.of(0, 20)).getContent().stream()
            .map(ProductSearchItem::productId).toList();
    }
}
