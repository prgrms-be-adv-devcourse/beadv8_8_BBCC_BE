package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색용 표를 만들 때 넘기는 값이다. 판매중인 상품 A(판매자 7, 카테고리 3)를 기준으로 옵션만 바꿔 쓴다.
 */
final class ProductSearchDocumentFixture {

    static final long PRODUCT_ID = 1L;
    static final long SELLER_ID = 7L;
    static final long CATEGORY_ID = 3L;
    static final String NAME = "상품 A";
    static final String THUMBNAIL_URL = "https://cdn.kidly.test/a-thumb.jpg";
    static final LocalDateTime PRODUCT_CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private ProductSearchDocumentFixture() {
    }

    static ProductSearchDocument.ChangeInfo changeInfo(List<ProductSearchDocument.OptionInfo> options) {
        return changeInfo(options, List.of());
    }

    static ProductSearchDocument.ChangeInfo changeInfo(
        List<ProductSearchDocument.OptionInfo> options, List<ProductSearchDocument.ValueInfo> infoValues) {
        return new ProductSearchDocument.ChangeInfo(PRODUCT_ID, SELLER_ID, NAME, CATEGORY_ID, ProductStatus.ON_SALE,
            THUMBNAIL_URL, PRODUCT_CREATED_AT, false, options, infoValues, OCCURRED_AT);
    }

    // 판매가와 품절 · 판매 상태, 옵션 값 조합을 정한 옵션이다
    static ProductSearchDocument.OptionInfo option(
        long price, boolean soldOut, OptionSaleStatus saleStatus, ProductSearchDocument.ValueInfo... values) {
        return new ProductSearchDocument.OptionInfo(price, soldOut, saleStatus, List.of(values));
    }

    static ProductSearchDocument.ValueInfo value(String code, Long valueId) {
        return new ProductSearchDocument.ValueInfo(code, valueId);
    }
}
