package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;

public interface InventoryRepository {

    // 재고 행과 재고 조정 기록을 함께 저장한다
    void saveOpening(InventoryOpening opening);

    // 판매 가능 수량이 qty 이상일 때만 줄이고, 줄였으면 true를 돌려준다 (INV-01)
    boolean deductIfAvailable(long optionId, int qty, LocalDateTime now);
}
