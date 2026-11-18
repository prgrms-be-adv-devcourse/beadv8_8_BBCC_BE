package com.bbcc.kidly.boundedcontext.search;

import com.bbcc.kidly.boundedcontext.search.domain.SearchProduct;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 문서를 만들 때 넘기는 값이다. 판매중인 상품 A(판매자 7, 카테고리 3)를 기준으로 필요한 칸만 바꿔 쓴다.
 */
final class SearchProductFixture {

    static final long PRODUCT_ID = 1L;
    static final long SELLER_ID = 7L;
    static final long CATEGORY_ID = 3L;
    static final String NAME = "상품 A";
    static final String THUMBNAIL_URL = "https://cdn.kidly.test/a-thumb.jpg";
    static final LocalDateTime PRODUCT_CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private SearchProductFixture() {
    }

    // 상품 A: 판매중, 25,000원 옵션 하나, 브랜드 · 설명 · 정보고시 값 없음
    static ChangeInfoBuilder aChangeInfo() {
        return new ChangeInfoBuilder();
    }

    static SearchProduct.ChangeInfo changeInfo(List<SearchProduct.OptionInfo> options) {
        return aChangeInfo().options(options).build();
    }

    static SearchProduct.ChangeInfo changeInfo(
        List<SearchProduct.OptionInfo> options, List<SearchProduct.AttributeEntryInfo> infoValues) {
        return aChangeInfo().options(options).infoValues(infoValues).build();
    }

    // 판매가와 품절 · 판매 상태, 옵션 값 조합을 정한 옵션이다
    static SearchProduct.OptionInfo option(
        long price, boolean soldOut, OptionSaleStatus saleStatus, SearchProduct.AttributeEntryInfo... values) {
        return new SearchProduct.OptionInfo(price, soldOut, saleStatus, List.of(values));
    }

    static SearchProduct.AttributeEntryInfo value(String code, Long valueId) {
        return new SearchProduct.AttributeEntryInfo(code, valueId);
    }

    static final class ChangeInfoBuilder {

        private Long productId = PRODUCT_ID;
        private Long sellerId = SELLER_ID;
        private String name = NAME;
        private Long categoryId = CATEGORY_ID;
        private ProductStatus status = ProductStatus.ON_SALE;
        private String thumbnailUrl = THUMBNAIL_URL;
        private String brand;
        private LocalDateTime productCreatedAt = PRODUCT_CREATED_AT;
        private boolean sizeFree;
        private List<SearchProduct.OptionInfo> options = List.of(option(25_000L, false, OptionSaleStatus.ON_SALE));
        private List<SearchProduct.AttributeEntryInfo> infoValues = List.of();
        private LocalDateTime occurredAt = OCCURRED_AT;
        private String description;

        private ChangeInfoBuilder() {
        }

        ChangeInfoBuilder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        ChangeInfoBuilder sellerId(Long sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        ChangeInfoBuilder name(String name) {
            this.name = name;
            return this;
        }

        ChangeInfoBuilder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        ChangeInfoBuilder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        ChangeInfoBuilder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        ChangeInfoBuilder brand(String brand) {
            this.brand = brand;
            return this;
        }

        ChangeInfoBuilder productCreatedAt(LocalDateTime productCreatedAt) {
            this.productCreatedAt = productCreatedAt;
            return this;
        }

        ChangeInfoBuilder sizeFree(boolean sizeFree) {
            this.sizeFree = sizeFree;
            return this;
        }

        ChangeInfoBuilder options(List<SearchProduct.OptionInfo> options) {
            this.options = options;
            return this;
        }

        ChangeInfoBuilder infoValues(List<SearchProduct.AttributeEntryInfo> infoValues) {
            this.infoValues = infoValues;
            return this;
        }

        ChangeInfoBuilder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        ChangeInfoBuilder description(String description) {
            this.description = description;
            return this;
        }

        SearchProduct.ChangeInfo build() {
            return new SearchProduct.ChangeInfo(productId, sellerId, name, categoryId, status, thumbnailUrl, brand,
                productCreatedAt, sizeFree, options, infoValues, occurredAt, description);
        }
    }
}
