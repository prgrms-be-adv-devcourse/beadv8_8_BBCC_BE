package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementItemRepository {

    SettlementItem save(SettlementItem item);

    Optional<SettlementItem> findById(Long id);

    boolean existsByOrderItemId(Long orderItemId);

    List<SettlementItem> findAllBySellerIdAndStatusAndConfirmedAtBefore(
        Long sellerId, SettlementItemStatus status, LocalDateTime confirmedAt);

    // 정산 실행이 묶음 안에서 충돌(버전 · 유니크)을 바로 드러내 그 판매자만 건너뛰게 한다
    void flush();
}
