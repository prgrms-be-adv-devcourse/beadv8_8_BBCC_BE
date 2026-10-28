package com.bbcc.kidly.boundedcontext.product.in.search.dto;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;

/**
 * 상품 목록의 카드 하나다. 상품 ID는 문자열로 보내고, 없는 썸네일 · 상호명은 null로 보낸다.
 */
public record ProductListItemResponse(String productId, String name, String thumbnailUrl, String businessName,
    long minPrice, boolean soldOut, boolean sizeFree) {

    public static ProductListItemResponse from(ProductSearchItem item) {
        return new ProductListItemResponse(String.valueOf(item.productId()), item.name(), item.thumbnailUrl(),
            item.businessName(), item.minPrice(), item.soldOut(), item.sizeFree());
    }
}
