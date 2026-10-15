package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface InventoryDeductionRepository {

    // 같은 주문 생성됨 이벤트를 다시 받았는지 판단하려고, 주문상품 ID 목록으로 기존 차감 기록을 찾는다.
    // 주문상품마다 차감 기록은 하나뿐이라(INV-10, order_item_id UNIQUE) 주문상품 ID마다 많아야 한 건이 나온다
    List<InventoryDeduction> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

    // 차감 요청이 만든 차감 기록을 저장한다. 이미 기록이 있는 주문상품을 저장하면 UNIQUE 제약 때문에 예외가 난다
    <S extends InventoryDeduction> List<S> saveAll(Iterable<S> records);

    // DEDUCTED일 때만 RESTORED로 바꾸고, 바꿨으면 true를 돌려준다
    // 이미 RESTORED거나 FAILED면 false라 같은 복구가 두 번 일어나지 않는다 (INV-04)
    boolean restoreIfDeducted(long deductionId, LocalDateTime now);
}
