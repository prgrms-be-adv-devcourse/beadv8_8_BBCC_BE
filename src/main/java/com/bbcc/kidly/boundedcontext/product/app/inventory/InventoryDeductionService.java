package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductionRecorder;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductor;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryShortageException;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 생성됨을 받아 주문상품마다 재고를 차감하는 흐름이다. 규칙 판단은 InventoryDeductionRequest가 하고,
 * 서비스는 읽기와 호출 순서만 맡는다. 트랜잭션은 InventoryDeductor가 직접 열어서 이 클래스에는 붙이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InventoryDeductionService {

    private final ProductRepository productRepository;
    private final InventoryDeductor inventoryDeductor;
    private final InventoryDeductionRecorder inventoryDeductionRecorder;

    public void deduct(OrderCreatedEvent event) {
        OrderCreatedEvent.Data data = event.data();
        List<Long> optionIds = new ArrayList<>();
        for (OrderCreatedEvent.Item item : data.items()) {
            optionIds.add(item.optionId());
        }
        Map<Long, ProductOption> optionById = new HashMap<>();
        for (ProductOption option : productRepository.findOptionsWithProduct(optionIds)) {
            optionById.put(option.getId(), option);
        }

        List<InventoryDeductionRequest.Item> items = new ArrayList<>();
        for (OrderCreatedEvent.Item item : data.items()) {
            items.add(new InventoryDeductionRequest.Item(
                item.orderItemId(), optionById.get(item.optionId()), item.quantity()));
        }
        InventoryDeductionRequest request = InventoryDeductionRequest.of(data.orderId(), items);
        // 차감은 영속성 컨텍스트를 비워 옵션의 상품을 더 읽을 수 없으므로, 판매 상태는 첫 차감 전에 판단한다
        if (!request.isPurchasable()) {
            inventoryDeductionRecorder.recordFailed(request);
            return;
        }
        try {
            inventoryDeductor.deduct(request);
        } catch (InventoryShortageException e) {
            // 차감 트랜잭션은 이미 롤백됐다. 주문상품마다 FAILED 기록을 새 트랜잭션으로 남긴다
            inventoryDeductionRecorder.recordFailed(request);
        }
    }
}
