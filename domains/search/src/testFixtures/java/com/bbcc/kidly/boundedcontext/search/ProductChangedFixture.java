package com.bbcc.kidly.boundedcontext.search;

import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.NAME;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.PRODUCT_CREATED_AT;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.PRODUCT_ID;
import static com.bbcc.kidly.boundedcontext.search.SearchProductFixture.SELLER_ID;

import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 검색이 받는 상품 정보 변경됨 이벤트를 만든다. {@link SearchProductFixture}와 같은 상품 A를 기준으로 필요한 칸만 바꿔 쓴다.
 */
final class ProductChangedFixture {

    private ProductChangedFixture() {
    }

    // 상품 A: 판매중, 25,000원 옵션 하나, 썸네일 · 브랜드 · 설명 · 정보고시 값 없음
    static Builder aProductChanged() {
        return new Builder();
    }

    // 판매가와 품절 여부, 옵션 값 조합을 정한 판매중 옵션이다
    static ProductChangedEvent.Option option(
        long price, boolean soldOut, ProductChangedEvent.AttributeEntry... values) {
        return new ProductChangedEvent.Option(1L, "옵션", "110", price, null, null, soldOut,
            OptionSaleStatus.ON_SALE, List.of(values));
    }

    static final class Builder {

        private Long productId = PRODUCT_ID;
        private Long sellerId = SELLER_ID;
        private String name = NAME;
        private Long categoryId = CATEGORY_ID;
        private ProductStatus status = ProductStatus.ON_SALE;
        private String thumbnailUrl;
        private LocalDateTime createdAt = PRODUCT_CREATED_AT;
        private boolean sizeFree;
        private List<ProductChangedEvent.Option> options = List.of(option(25_000L, false));
        private List<ProductChangedEvent.AttributeEntry> infoValues = List.of();
        private String brand;
        private String description;
        private LocalDateTime occurredAt = OCCURRED_AT;

        private Builder() {
        }

        Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        Builder sellerId(Long sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        Builder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Builder sizeFree(boolean sizeFree) {
            this.sizeFree = sizeFree;
            return this;
        }

        Builder options(ProductChangedEvent.Option... options) {
            this.options = List.of(options);
            return this;
        }

        Builder infoValues(ProductChangedEvent.AttributeEntry... infoValues) {
            this.infoValues = List.of(infoValues);
            return this;
        }

        Builder brand(String brand) {
            this.brand = brand;
            return this;
        }

        Builder description(String description) {
            this.description = description;
            return this;
        }

        Builder occurredAt(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        ProductChangedEvent.Data data() {
            return new ProductChangedEvent.Data(productId, sellerId, name, categoryId, status, thumbnailUrl,
                createdAt, sizeFree, options, infoValues, brand, description, List.of(), List.of(), false, null);
        }

        ProductChangedEvent event() {
            return new ProductChangedEvent(UUID.randomUUID(), occurredAt, data());
        }
    }
}
