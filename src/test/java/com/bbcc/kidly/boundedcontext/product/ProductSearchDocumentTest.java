package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상품 정보 변경됨에서 옮긴 값으로 검색용 표 한 행을 만들 때, 목록에 보여 줄 값을 규칙대로 계산하는지 확인한다.
 */
class ProductSearchDocumentTest {

    @Test
    @DisplayName("PRICE-05 표시 가격은 판매할 수 있는 옵션 중 최저가다. 더 싼 품절 옵션과 판매중지 옵션은 고르지 않는다")
    void minPriceIsLowestOfPurchasableOptions() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(List.of(
            option(25_000L, false, OptionSaleStatus.ON_SALE),
            option(20_000L, true, OptionSaleStatus.ON_SALE),
            option(15_000L, false, OptionSaleStatus.STOPPED),
            option(22_000L, false, OptionSaleStatus.ON_SALE))));

        assertThat(document.getMinPrice()).isEqualTo(22_000L);
    }

    @Test
    @DisplayName("PRICE-05 · SALE-07 모든 옵션이 품절이면 판매중지 옵션까지 전체 옵션 중 최저가를 두고 품절로 표시한다")
    void allSoldOutKeepsLowestOfAllOptionsAndMarksSoldOut() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(List.of(
            option(25_000L, true, OptionSaleStatus.ON_SALE),
            option(22_000L, true, OptionSaleStatus.STOPPED))));

        assertThat(document.getMinPrice()).isEqualTo(22_000L);
        assertThat(document.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("SALE-07 · PRICE-05 판매중지 옵션만 재고가 남으면 품절이 아니고, 판매할 수 있는 옵션이 없어 전체 옵션 중 최저가를 둔다")
    void stoppedOptionInStockIsNotSoldOut() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(List.of(
            option(25_000L, true, OptionSaleStatus.ON_SALE),
            option(22_000L, false, OptionSaleStatus.STOPPED))));

        assertThat(document.isSoldOut()).isFalse();
        assertThat(document.getMinPrice()).isEqualTo(22_000L);
    }

    @Test
    @DisplayName("attributes는 옵션 값과 상품 정보 값의 값 ID를 항목 코드별로 모은다. 같은 값은 한 번만 ID 오름차순으로 담고, 판매중지 · 품절 옵션의 값도 담는다")
    void attributesCollectValueIdsByCode() {
        ProductSearchDocument document = ProductSearchDocument.create(changeInfo(
            List.of(
                option(25_000L, false, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 13L)),
                option(25_000L, true, OptionSaleStatus.ON_SALE, value("COLOR", 5L), value("SIZE", 12L)),
                option(25_000L, false, OptionSaleStatus.STOPPED, value("COLOR", 3L), value("SIZE", 12L))),
            List.of(value("MAIN_MATERIAL", 20L), value("FEATURE", 33L), value("FEATURE", 32L))));

        assertThat(document.getAttributes()).isEqualTo(Map.of(
            "COLOR", List.of(3L, 5L),
            "SIZE", List.of(12L, 13L),
            "MAIN_MATERIAL", List.of(20L),
            "FEATURE", List.of(32L, 33L)));
    }
}
