package com.bbcc.kidly.boundedcontext.search;

import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 검색이 받는 상품 · 판매자 이벤트를 만든다. 옵션 하나(가격 · 색상 값 ID 하나)짜리 상품이다.
 */
final class SearchEvents {

    static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private SearchEvents() {
    }

    static ProductChangedEvent product(long productId, String name, long sellerId, long categoryId,
        ProductStatus status, long price, long colorValueId, int createdDay) {
        return product(productId, name, sellerId, categoryId, status, price, colorValueId, createdDay, OCCURRED_AT);
    }

    static ProductChangedEvent product(long productId, String name, long sellerId, long categoryId,
        ProductStatus status, long price, long colorValueId, int createdDay, LocalDateTime occurredAt) {
        ProductChangedEvent.Option option = new ProductChangedEvent.Option(productId * 10, "기본", "110", price,
            null, null, false, OptionSaleStatus.ON_SALE,
            List.of(new ProductChangedEvent.AttributeEntry("COLOR", colorValueId, "색상 " + colorValueId)));
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(productId, sellerId,
            name, categoryId, status, "https://cdn.kidly.test/" + productId + ".jpg",
            LocalDateTime.of(2026, 10, createdDay, 9, 0), false, List.of(option)));
    }

    static SellerChangedEvent seller(long sellerId, String businessName) {
        return new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(sellerId, businessName, SellerStatus.ACTIVE));
    }
}
