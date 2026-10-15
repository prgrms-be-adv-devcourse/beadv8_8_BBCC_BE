package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.CREATED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.document;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchFixture.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
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
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ProductStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ON_SALE")
    @DisplayName("SALE-06 판매대기 · 판매중지 상품은 목록에 나오지 않고 판매중 상품만 나온다")
    void onlyOnSaleProducts(ProductStatus hiddenStatus) {
        persist(
            document(1L).build(),
            document(2L).status(hiddenStatus).build());

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

    @ParameterizedTest(name = "{0} → 상품 {1}")
    @CsvSource({
        "NEWEST, '4,3,2,1'",
        "PRICE_ASC, '4,2,3,1'",
        "PRICE_DESC, '3,1,2,4'"})
    @DisplayName("등록 최신순 · 가격 낮은순 · 가격 높은순으로 정렬하고, 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다")
    void sortProducts(ProductSearchSort sort, String expectedIds) {
        persist(
            document(1L).price(30_000L).build(),
            document(2L).price(20_000L).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).price(30_000L).createdAt(CREATED_AT.plusDays(2)).build(),
            document(4L).price(10_000L).createdAt(CREATED_AT.plusDays(2)).build());

        Page<ProductSearchItem> result = productSearch.search(
            new ProductSearchCondition(null, null, Map.of(), sort), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
    }

    @ParameterizedTest(name = "page {0}, size {1} → 상품 {2}")
    @CsvSource({
        "0, 2, '5,4', true",
        "1, 2, '3,2', true",
        "2, 2, 1, false",
        "3, 2, '', false"})
    @DisplayName("page와 size대로 잘라 돌려주고, 전체 개수는 거른 조건으로 센다")
    void pageProducts(int page, int size, String expectedIds, boolean hasNext) {
        persist(
            document(1L).build(),
            document(2L).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).createdAt(CREATED_AT.plusDays(2)).build(),
            document(4L).createdAt(CREATED_AT.plusDays(3)).build(),
            document(5L).createdAt(CREATED_AT.plusDays(4)).build(),
            document(6L).status(ProductStatus.STOPPED).build());

        Page<ProductSearchItem> result = productSearch.search(noCondition(), PageRequest.of(page, size));

        assertThat(result.getContent()).extracting(ProductSearchItem::productId).containsExactly(ids(expectedIds));
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isEqualTo(hasNext);
    }

    @Test
    @DisplayName("판매자 복사본이 있으면 그 상호명을 붙이고, 없으면 상호명만 null이다. 정지된 판매자의 상품도 숨기지 않는다")
    void attachBusinessName() {
        persist(
            ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved()),
            ProductReplicaSeller.create(new ProductReplicaSeller.ChangeInfo(
                3L, "정지상점", SellerStatus.SUSPENDED, ProductReplicaSellerFixture.SELLER_CHANGED_AT)),
            document(1L).sellerId(ProductReplicaSellerFixture.SELLER_ID).build(),
            document(2L).sellerId(3L).createdAt(CREATED_AT.plusDays(1)).build(),
            document(3L).sellerId(9L).createdAt(CREATED_AT.plusDays(2)).build());

        Page<ProductSearchItem> result = productSearch.search(noCondition(), PageRequest.of(0, 20));

        assertThat(result.getContent())
            .extracting(ProductSearchItem::productId, ProductSearchItem::businessName)
            .containsExactly(
                tuple(3L, null),
                tuple(2L, "정지상점"),
                tuple(1L, ProductReplicaSellerFixture.BUSINESS_NAME));
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

    private void persist(Object... entities) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Object entity : entities) {
                entityManager.persist(entity);
            }
        });
    }
}
