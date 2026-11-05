package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearch;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.search.domain.ProductEmbedding;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.boundedcontext.search.domain.SearchProductRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import com.bbcc.kidly.support.SearchIndexes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 의미 검색 벡터를 실제 Elasticsearch에 저장하고, 관련도순 검색이 kNN으로 뜻이 가까운 상품을 더하는지 확인한다(#797).
 * 임베딩 API는 부르지 않는다. 벡터를 직접 만들어 저장소에 넣고, 검색어 벡터도 조건으로 직접 넘긴다.
 * 벡터는 축 하나만 1인 단위 벡터(와 그 조합)라 코사인 유사도를 손으로 셀 수 있다. 기준 유사도는 0.3(search.yml)이다.
 */
@IntegrationTest
class SemanticSearchIntegrationTest {

    private static final int DIMENSIONS = 512;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 3, 10, 0);

    @Autowired
    private SearchProductRepository repository;

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private SearchIndexes indexes;

    @BeforeEach
    void indexProducts() {
        // 검색어 벡터(0번 축)와 유사도 1.0
        save(1L, "래시가드", ProductStatus.ON_SALE, axis(0));
        // 유사도 0. 기준보다 멀어 의미 검색으로는 나오지 않는다
        save(2L, "여아 원피스", ProductStatus.ON_SALE, axis(1));
        // 유사도 약 0.707
        save(3L, "남아 수영복", ProductStatus.ON_SALE, mix(0, 2));
        // 유사도 1.0이지만 판매중지라 나오지 않는다
        save(4L, "아동 래시가드 세트", ProductStatus.STOPPED, axis(0));
        // 벡터 없이 저장된 상품(임베딩 API를 쓸 수 없었음)
        save(5L, "원피스 수영복", ProductStatus.ON_SALE, null);
        indexes.refresh();
    }

    @Test
    @DisplayName("저장한 벡터와 문장 해시를 다시 읽는다. 벡터 없이 저장된 상품은 비어 있다")
    void readsStoredEmbedding() {
        assertThat(repository.findEmbedding(1L)).hasValueSatisfying(embedding -> {
            assertThat(embedding.textHash()).isEqualTo(product(1L, "래시가드", ProductStatus.ON_SALE)
                .getEmbeddingTextHash());
            assertThat(embedding.vector()).isEqualTo(axis(0));
        });
        assertThat(repository.findEmbedding(5L)).isEmpty();
        assertThat(repository.findEmbedding(99L)).isEmpty();
    }

    @Test
    @DisplayName("상품명에 없는 검색어도 뜻이 가까운 판매중 상품을 가까운 순으로 찾는다. 기준보다 먼 상품은 넣지 않는다")
    void findsBySimilarity() {
        Page<ProductSearchItem> page = search("물놀이 옷", axis(0));

        assertThat(ids(page)).containsExactly(1L, 3L);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품명에 검색어가 있는 상품을 먼저 보이고, 뜻이 가까운 상품을 뒤에 더한다")
    void keywordMatchesFirst() {
        // 2 · 5는 검색어가 있어 먼저(벡터가 멀거나 없어 점수가 같아 상품 ID 큰 순), 1 · 3은 뜻이 가까운 순
        assertThat(ids(search("원피스", axis(0)))).containsExactly(5L, 2L, 1L, 3L);
    }

    @Test
    @DisplayName("검색어 벡터가 없으면(임베딩 API를 쓸 수 없음) 상품명 · 브랜드로만 찾는다")
    void keywordOnlyWithoutVector() {
        assertThat(ids(search("원피스", null))).containsExactlyInAnyOrder(5L, 2L);
        assertThat(ids(search("물놀이 옷", null))).isEmpty();
    }

    private Page<ProductSearchItem> search(String keyword, List<Float> vector) {
        return productSearch.search(new ProductSearchCondition(null, keyword, Map.of(), ProductSearchSort.RELEVANCE,
            vector), PageRequest.of(0, 20));
    }

    private void save(long productId, String name, ProductStatus status, List<Float> vector) {
        SearchProduct product = product(productId, name, status);
        if (vector != null) {
            product.attachEmbedding(new ProductEmbedding(product.getEmbeddingTextHash(), vector));
        }
        repository.saveIfNewer(product);
    }

    private static SearchProduct product(long productId, String name, ProductStatus status) {
        return SearchProduct.create(new SearchProduct.ChangeInfo(productId, 7L, name, 3L, status, null, null,
            OCCURRED_AT.minusDays(productId), false,
            List.of(new SearchProduct.OptionInfo(15_000L, false, OptionSaleStatus.ON_SALE, List.of())), List.of(),
            OCCURRED_AT));
    }

    private static List<Float> axis(int index) {
        List<Float> vector = new ArrayList<>(Collections.nCopies(DIMENSIONS, 0.0f));
        vector.set(index, 1.0f);
        return vector;
    }

    // 두 축의 합. 한 축의 단위 벡터와 코사인 유사도가 1/√2이다
    private static List<Float> mix(int first, int second) {
        List<Float> vector = axis(first);
        vector.set(second, 1.0f);
        return vector;
    }

    private static List<Long> ids(Page<ProductSearchItem> page) {
        return page.getContent().stream().map(ProductSearchItem::productId).toList();
    }
}
