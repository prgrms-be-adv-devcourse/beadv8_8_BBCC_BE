package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

public interface PurchaseRecordRepository {

    PurchaseRecord save(PurchaseRecord purchaseRecord);

    boolean existsByOrderItemId(Long orderItemId);
}
