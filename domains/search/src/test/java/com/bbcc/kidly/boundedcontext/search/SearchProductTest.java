package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.NAME;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.PRODUCT_CREATED_AT;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.option;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 상품 정보 변경됨에서 옮긴 값으로 검색 문서를 만들 때, 목록에 보여 줄 값을 상품 검색용 표와 같은 규칙으로 계산하는지 확인한다.
 */
class SearchProductTest {

    @Test
    @DisplayName("PRICE-05 표시 가격은 판매할 수 있는 옵션 중 최저가다. 더 싼 품절 옵션과 판매중지 옵션은 고르지 않는다")
    void minPriceIsLowestOfPurchasableOptions() {
        SearchProduct product = SearchProduct.create(changeInfo(List.of(
            option(25_000L, false, OptionSaleStatus.ON_SALE),
            option(20_000L, true, OptionSaleStatus.ON_SALE),
            option(15_000L, false, OptionSaleStatus.STOPPED),
            option(22_000L, false, OptionSaleStatus.ON_SALE))));

        assertThat(product.getMinPrice()).isEqualTo(22_000L);
    }

    @Test
    @DisplayName("PRICE-05 · SALE-07 모든 옵션이 품절이면 판매중지 옵션까지 전체 옵션 중 최저가를 두고 품절로 표시한다")
    void allSoldOutKeepsLowestOfAllOptionsAndMarksSoldOut() {
        SearchProduct product = SearchProduct.create(changeInfo(List.of(
            option(25_000L, true, OptionSaleStatus.ON_SALE),
            option(22_000L, true, OptionSaleStatus.STOPPED))));

        assertThat(product.getMinPrice()).isEqualTo(22_000L);
        assertThat(product.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("SALE-07 · PRICE-05 판매중지 옵션만 재고가 남으면 품절이 아니고, 판매할 수 있는 옵션이 없어 전체 옵션 중 최저가를 둔다")
    void stoppedOptionInStockIsNotSoldOut() {
        SearchProduct product = SearchProduct.create(changeInfo(List.of(
            option(25_000L, true, OptionSaleStatus.ON_SALE),
            option(22_000L, false, OptionSaleStatus.STOPPED))));

        assertThat(product.isSoldOut()).isFalse();
        assertThat(product.getMinPrice()).isEqualTo(22_000L);
    }

    @Test
    @DisplayName("attributes는 옵션 값과 상품 정보 값의 값 ID를 항목 코드별로 모은다. 같은 값은 한 번만 ID 오름차순으로 담고, 판매중지 · 품절 옵션의 값도 담는다")
    void attributesCollectValueIdsByCode() {
        SearchProduct product = SearchProduct.create(changeInfo(
            List.of(
                option(25_000L, false, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 13L)),
                option(25_000L, true, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 12L)),
                option(25_000L, false, OptionSaleStatus.STOPPED, value("COLOR", 3L), value("SIZE", 12L))),
            List.of(value("MAIN_MATERIAL", 20L), value("FEATURE", 33L), value("FEATURE", 32L))));

        assertThat(product.getAttributes()).isEqualTo(Map.of(
            "COLOR", List.of(3L, 5L),
            "SIZE", List.of(12L, 13L),
            "MAIN_MATERIAL", List.of(20L),
            "FEATURE", List.of(32L, 33L)));
    }

    @Test
    @DisplayName("직접 입력 항목은 값 ID가 없어 attributes에 넣지 않는다")
    void textInfoValueIsNotInAttributes() {
        SearchProduct product = SearchProduct.create(changeInfo(
            List.of(option(25_000L, false, OptionSaleStatus.ON_SALE)),
            List.of(value("MAIN_MATERIAL", 20L), value("FABRIC_COMPOSITION", null))));

        assertThat(product.getAttributes()).isEqualTo(Map.of("MAIN_MATERIAL", List.of(20L)));
    }

    @Test
    @DisplayName("이름 · 썸네일 · 판매자 · 카테고리 · 판매 상태 · 사이즈 무관 · 상품 등록 시각 · 이벤트 발생 시각은 받은 값 그대로 옮긴다")
    void copiesOtherValues() {
        LocalDateTime productCreatedAt = LocalDateTime.of(2026, 10, 2, 11, 0);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 15, 12, 30);
        SearchProduct product = SearchProduct.create(new SearchProduct.ChangeInfo(
            5L, 9L, "아기 모자", 4L, ProductStatus.STOPPED, "https://cdn.kidly.test/hat.jpg", productCreatedAt, true,
            List.of(option(18_000L, false, OptionSaleStatus.ON_SALE)), List.of(), occurredAt));

        assertThat(product)
            .extracting(SearchProduct::getProductId, SearchProduct::getName, SearchProduct::getThumbnailUrl,
                SearchProduct::getSellerId, SearchProduct::getCategoryId, SearchProduct::getStatus,
                SearchProduct::isSizeFree, SearchProduct::getProductCreatedAt, SearchProduct::getOccurredAt)
            .containsExactly(5L, "아기 모자", "https://cdn.kidly.test/hat.jpg", 9L, 4L, ProductStatus.STOPPED, true,
                productCreatedAt, occurredAt);
    }

    @Test
    @DisplayName("썸네일은 선택 입력이라 없어도 만든다")
    void createsWithoutThumbnail() {
        SearchProduct product = SearchProduct.create(new SearchProduct.ChangeInfo(
            PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID, ProductStatus.ON_SALE, null, PRODUCT_CREATED_AT, false,
            List.of(option(25_000L, false, OptionSaleStatus.ON_SALE)), List.of(), OCCURRED_AT));

        assertThat(product.getThumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("PRICE-05 옵션이 하나도 없으면 표시 가격을 정할 수 없어 만들지 않는다")
    void rejectsEmptyOptions() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SearchProduct.create(changeInfo(List.of())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changeInfosMissingRequiredValue")
    @DisplayName("받은 값 · 옵션 · 항목 값의 필수 값 중 하나라도 없으면 만들지 않는다")
    void rejectsMissingRequiredValue(String field, SearchProduct.ChangeInfo info) {
        assertThatNullPointerException()
            .isThrownBy(() -> SearchProduct.create(info))
            .withMessageStartingWith(field);
    }

    static Stream<Arguments> changeInfosMissingRequiredValue() {
        List<SearchProduct.OptionInfo> options = List.of(option(25_000L, false, OptionSaleStatus.ON_SALE));
        ProductStatus onSale = ProductStatus.ON_SALE;
        return Stream.of(
            arguments("info", null),
            arguments("productId", new SearchProduct.ChangeInfo(null, SELLER_ID, NAME, CATEGORY_ID, onSale,
                THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), OCCURRED_AT)),
            arguments("sellerId", new SearchProduct.ChangeInfo(PRODUCT_ID, null, NAME, CATEGORY_ID, onSale,
                THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), OCCURRED_AT)),
            arguments("name", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, null, CATEGORY_ID, onSale,
                THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), OCCURRED_AT)),
            arguments("categoryId", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, null, onSale,
                THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), OCCURRED_AT)),
            arguments("status", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID, null,
                THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), OCCURRED_AT)),
            arguments("productCreatedAt", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME,
                CATEGORY_ID, onSale, THUMBNAIL_URL, null, false, options, List.of(), OCCURRED_AT)),
            arguments("occurredAt", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID,
                onSale, THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, List.of(), null)),
            arguments("options", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID,
                onSale, THUMBNAIL_URL, PRODUCT_CREATED_AT, false, null, List.of(), OCCURRED_AT)),
            arguments("infoValues", new SearchProduct.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID,
                onSale, THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, null, OCCURRED_AT)),
            arguments("saleStatus", changeInfo(List.of(
                new SearchProduct.OptionInfo(25_000L, false, null, List.of())))),
            arguments("values", changeInfo(List.of(
                new SearchProduct.OptionInfo(25_000L, false, OptionSaleStatus.ON_SALE, null)))),
            arguments("code", changeInfo(options, List.of(value(null, 20L)))));
    }
}
