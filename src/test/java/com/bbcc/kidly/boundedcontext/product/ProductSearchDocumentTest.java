package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.changeInfo;
import static com.bbcc.kidly.boundedcontext.product.ProductSearchDocumentFixture.option;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import java.util.List;
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
}
