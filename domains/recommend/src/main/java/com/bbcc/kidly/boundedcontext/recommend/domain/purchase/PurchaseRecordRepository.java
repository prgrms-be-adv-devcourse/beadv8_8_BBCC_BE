package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import java.time.LocalDateTime;
import java.util.List;

public interface PurchaseRecordRepository {

    PurchaseRecord save(PurchaseRecord purchaseRecord);

    boolean existsByOrderItemId(Long orderItemId);

    /**
     * 그 자녀가 {@code since} 이후(같은 시각 포함)에 구매확정한 상품 ID를 중복 없이 읽는다. 순서는 정하지 않는다.
     */
    List<Long> findProductIdsConfirmedSince(Long childProfileId, LocalDateTime since);
}
