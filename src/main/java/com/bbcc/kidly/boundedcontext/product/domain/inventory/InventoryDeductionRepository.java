package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import java.util.Collection;
import java.util.List;

public interface InventoryDeductionRepository {

    // 같은 주문 생성됨 이벤트를 다시 받았는지 판단하려고, 주문상품 ID 목록으로 기존 차감 기록을 찾는다. 같은 주문상품의 차감 기록은 한 번만 만든다 (INV-10)
    List<InventoryDeduction> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

    // 차감 요청이 만든 차감 기록을 저장한다
    <S extends InventoryDeduction> List<S> saveAll(Iterable<S> records);
}
