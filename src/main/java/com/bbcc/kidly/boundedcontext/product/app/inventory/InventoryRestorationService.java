package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryRestorer;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRestoration;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 취소됨을 받아 그 주문에서 차감한 재고를 되돌리는 흐름이다. 규칙 판단은 InventoryRestoration이 하고,
 * 서비스는 읽기와 호출 순서만 맡는다. 트랜잭션은 InventoryRestorer가 새로 열어서 이 클래스에는 붙이지 않는다.
 * 바깥(리스너) 트랜잭션에서는 쓰기나 잠금을 하지 않아야 한다.
 * 바깥 트랜잭션이 잡은 잠금을 같은 스레드의 안쪽 새 트랜잭션이 기다리면 PostgreSQL이 교착으로 감지하지 못하고 멈추기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class InventoryRestorationService {

    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final InventoryRestorer inventoryRestorer;

    public void restore(OrderCancelledEvent event) {
        List<Long> orderItemIds = new ArrayList<>();
        for (OrderCancelledEvent.Item item : event.data().items()) {
            orderItemIds.add(item.orderItemId());
        }
        List<InventoryDeduction> records = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds);
        List<InventoryDeduction> targets = InventoryRestoration.of(records).deductedRecords();
        inventoryRestorer.restore(targets);
    }
}
