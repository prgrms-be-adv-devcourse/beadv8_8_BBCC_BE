package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument.AttributeEntryInfo;
import com.bbcc.kidly.boundedcontext.product.domain.search.ProductSearchDocument.OptionInfo;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 목록 조회 테스트가 검색용 표에 넣을 행을 만든다. 기본은 판매중 · 품절 아님 · 옵션 하나(25,000원)인 상품이다.
 */
final class ProductListFixture {

    static final long SELLER_ID = 7L;
    static final long CATEGORY_ID = 1L;
    static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    private ProductListFixture() {
    }

    static Document document(long productId) {
        return new Document(productId);
    }

    static AttributeEntryInfo value(String code, long valueId) {
        return new AttributeEntryInfo(code, valueId);
    }

    static final class Document {

        private final long productId;
        private String name;
        private long sellerId = SELLER_ID;
        private long categoryId = CATEGORY_ID;
        private ProductStatus status = ProductStatus.ON_SALE;
        private String thumbnailUrl;
        private LocalDateTime createdAt = CREATED_AT;
        private boolean sizeFree;
        private long price = 25_000L;
        private boolean soldOut;
        private List<AttributeEntryInfo> values = List.of();

        private Document(long productId) {
            this.productId = productId;
            this.name = "상품 " + productId;
        }

        Document name(String name) {
            this.name = name;
            return this;
        }

        Document sellerId(long sellerId) {
            this.sellerId = sellerId;
            return this;
        }

        Document categoryId(long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        Document status(ProductStatus status) {
            this.status = status;
            return this;
        }

        Document thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        Document createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        Document sizeFree() {
            this.sizeFree = true;
            return this;
        }

        Document price(long price) {
            this.price = price;
            return this;
        }

        Document soldOut() {
            this.soldOut = true;
            return this;
        }

        // 옵션 하나의 값 조합이다. 검색용 표는 이 값들을 항목 code별 값 ID 목록으로 모은다
        Document values(AttributeEntryInfo... values) {
            this.values = List.of(values);
            return this;
        }

        ProductSearchDocument build() {
            OptionInfo option = new OptionInfo(price, soldOut, OptionSaleStatus.ON_SALE, values);
            return ProductSearchDocument.create(new ProductSearchDocument.ChangeInfo(productId, sellerId, name,
                categoryId, status, thumbnailUrl, createdAt, sizeFree, List.of(option), List.of(), createdAt));
        }
    }
}
