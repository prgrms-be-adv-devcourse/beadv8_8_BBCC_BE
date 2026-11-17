package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

final class ProductReplicaSellerFixture {

    static final long SELLER_ID = 2L;

    static final String BUSINESS_NAME = "테스트상점";

    static final LocalDateTime SELLER_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private ProductReplicaSellerFixture() {
    }

    static ProductReplicaSeller.ChangeInfo sellerApproved() {
        return sellerChangeAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    static ProductReplicaSeller.ChangeInfo sellerChangeAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new ProductReplicaSeller.ChangeInfo(SELLER_ID, businessName, status, occurredAt);
    }

    static SellerChangedEvent sellerApprovedEvent() {
        return sellerChangedEventAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    static SellerChangedEvent sellerChangedEventAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(SELLER_ID, businessName, status));
    }
}
