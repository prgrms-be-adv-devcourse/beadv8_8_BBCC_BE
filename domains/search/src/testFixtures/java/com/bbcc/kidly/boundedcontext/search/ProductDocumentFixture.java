package com.bbcc.kidly.boundedcontext.search;

import com.bbcc.kidly.boundedcontext.search.out.ProductDocument;
import java.util.List;

/**
 * 색인에 저장된 상품 문서를 만든다. 벡터와 의미 검색 문장이 없는 문서(예전 문서와 같은 모양)를 기준으로 필요한 칸만 바꿔 쓴다.
 */
final class ProductDocumentFixture {

    private ProductDocumentFixture() {
    }

    // 판매자 7 · 카테고리 3의 판매중 상품. 15,000원, 품절 아님, 브랜드 · 썸네일 · 속성 값 없음
    static Builder aProductDocument() {
        return new Builder();
    }

    static final class Builder {

        private Long productId = SearchProductFixture.PRODUCT_ID;
        private String name = SearchProductFixture.NAME;
        private String thumbnailUrl;
        private Long sellerId = SearchProductFixture.SELLER_ID;
        private String status = "ON_SALE";
        private boolean sizeFree;
        private String productCreatedAt = "2026-10-01T09:00:00";
        private long minPrice = 15_000L;
        private String occurredAt = "2026-10-15T10:00:00";

        private Builder() {
        }

        Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        Builder sellerId(Long sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        Builder sizeFree(boolean sizeFree) {
            this.sizeFree = sizeFree;
            return this;
        }

        Builder occurredAt(String occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        ProductDocument build() {
            return new ProductDocument(productId, name, thumbnailUrl, null, sellerId, SearchProductFixture.CATEGORY_ID,
                status, sizeFree, productCreatedAt, minPrice, false, List.of(), occurredAt, null, null, null);
        }
    }
}
