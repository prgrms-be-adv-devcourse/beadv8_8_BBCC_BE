package com.bbcc.kidly.boundedcontext.settlement.domain;

import java.util.Optional;

/** 정산항목 영속성 계약. 구현은 out의 Spring Data JPA가 담당한다. */
public interface SettlementItemRepository {

    SettlementItem save(SettlementItem item);

    Optional<SettlementItem> findById(Long id);

    Optional<SettlementItem> findByOrderItemId(Long orderItemId);
}
