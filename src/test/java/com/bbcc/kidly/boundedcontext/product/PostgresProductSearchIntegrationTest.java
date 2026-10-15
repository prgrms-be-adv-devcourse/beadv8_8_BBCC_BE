package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.document;
import static com.bbcc.kidly.boundedcontext.product.ProductListFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 목록 조회가 검색용 표를 실제 PostgreSQL에서 거르고 정렬하고 자르는지 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class PostgresProductSearchIntegrationTest {

    @Autowired
    private ProductSearch productSearch;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SALE-06 판매중지 상품은 목록에 나오지 않고 판매중 상품만 나온다")
    void onlyOnSaleProducts() {
        persist(
            document(1L).build(),
            document(2L).status(ProductStatus.STOPPED).build());

        Page<ProductSearchItem> result = productSearch.search(noCondition(), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(1L);
    }

    @Test
    @DisplayName("SALE-07 모든 옵션이 품절인 상품도 목록에 남고, 품절 표시와 검색용 표의 표시 가격(PRICE-05)을 가진다")
    void soldOutProductStaysWithDisplayPrice() {
        persist(document(1L).name("품절 상품").thumbnailUrl("https://cdn.kidly.test/1.jpg")
            .price(31_000L).soldOut().sizeFree().build());

        Page<ProductSearchItem> result = productSearch.search(noCondition(), PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(
            new ProductSearchItem(1L, "품절 상품", "https://cdn.kidly.test/1.jpg", null, 31_000L, true, true));
    }

    @ParameterizedTest(name = "카테고리 {0} → 상품 {1}")
    @CsvSource({
        "1, '3,1'",
        "2, 2",
        "9, ''",
        ", '3,2,1'"})
    @DisplayName("카테고리를 고르면 그 카테고리 ID와 같은 상품만 나오고, 고르지 않으면 판매중 상품이 모두 나온다")
    void filterByCategory(Long categoryId, String expectedIds) {
        persist(
            document(1L).categoryId(1L).build(),
            document(2L).categoryId(2L).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).categoryId(1L).createdAt(CREATED_AT.plusDays(2)).build());

        Page<ProductSearchItem> result = productSearch.search(
            new ProductSearchCondition(categoryId, null, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
    }

    @ParameterizedTest(name = "키워드 [{0}] → 상품 {1}")
    @CsvSource({
        "맨투맨, 1",
        "HOODIE, 2",
        "kids, 2",
        "'%', 3",
        "_, 4",
        "없는 상품, ''",
        "'', '4,3,2,1'",
        ", '4,3,2,1'"})
    @DisplayName("키워드는 상품명에 들어간 글자로 찾고 대소문자를 무시하며, %와 _는 글자 그대로 찾는다. 비어 있으면 거르지 않는다")
    void filterByKeyword(String keyword, String expectedIds) {
        persist(
            document(1L).name("아기 맨투맨").build(),
            document(2L).name("Kids Hoodie").createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).name("면 100% 티셔츠").createdAt(CREATED_AT.plusDays(2)).build(),
            document(4L).name("세트_반바지").createdAt(CREATED_AT.plusDays(3)).build());

        Page<ProductSearchItem> result = productSearch.search(
            new ProductSearchCondition(null, keyword, Map.of(), ProductSearchSort.NEWEST), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
    }

    @ParameterizedTest(name = "항목 값 {0} → 상품 {1}")
    @CsvSource({
        "'COLOR:3', 1",
        "'COLOR:3,COLOR:5', '2,1'",
        "'COLOR:9', ''"})
    @DisplayName("같은 항목에서 값을 여러 개 고르면 그중 하나만 가진 상품도 나온다")
    void filterByValuesOfSameAttribute(String attributeSpec, String expectedIds) {
        persist(
            document(1L).values(value("COLOR", 3L)).build(),
            document(2L).values(value("COLOR", 5L)).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).values(value("COLOR", 7L)).createdAt(CREATED_AT.plusDays(2)).build());

        Page<ProductSearchItem> result = productSearch.search(
            new ProductSearchCondition(null, null, attributes(attributeSpec), ProductSearchSort.NEWEST),
            PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
    }

    @ParameterizedTest(name = "항목 값 {0} → 상품 {1}")
    @CsvSource({
        "'COLOR:3,SIZE:15', 1",
        "'COLOR:3,COLOR:5,SIZE:15', '3,1'",
        "'COLOR:5,SIZE:16', ''"})
    @DisplayName("서로 다른 항목에서 값을 고르면 항목마다 고른 값 중 하나를 모두 가진 상품만 나온다")
    void filterByValuesOfDifferentAttributes(String attributeSpec, String expectedIds) {
        persist(
            document(1L).values(value("COLOR", 3L), value("SIZE", 15L)).build(),
            document(2L).values(value("COLOR", 3L), value("SIZE", 16L)).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).values(value("COLOR", 5L), value("SIZE", 15L)).createdAt(CREATED_AT.plusDays(2)).build());

        Page<ProductSearchItem> result = productSearch.search(
            new ProductSearchCondition(null, null, attributes(attributeSpec), ProductSearchSort.NEWEST),
            PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
    }

    // CSV의 "COLOR:3,SIZE:15"를 항목 code별 값 ID 목록으로 묶는다
    private static Map<String, List<Long>> attributes(String spec) {
        Map<String, List<Long>> valueIdsByCode = new LinkedHashMap<>();
        for (String entry : spec.split(",")) {
            String[] codeAndId = entry.split(":");
            valueIdsByCode.computeIfAbsent(codeAndId[0], code -> new ArrayList<>()).add(Long.valueOf(codeAndId[1]));
        }
        return valueIdsByCode;
    }

    // CSV의 "3,2,1"을 기대하는 상품 ID 배열로 바꾼다. 빈 문자열이면 빈 배열이다
    private static Long[] ids(String csv) {
        if (csv == null || csv.isBlank()) {
            return new Long[0];
        }
        return Arrays.stream(csv.split(",")).map(Long::valueOf).toArray(Long[]::new);
    }

    private ProductSearchCondition noCondition() {
        return new ProductSearchCondition(null, null, Map.of(), ProductSearchSort.NEWEST);
    }

    private void persist(ProductSearchDocument... documents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ProductSearchDocument document : documents) {
                entityManager.persist(document);
            }
        });
    }
}
