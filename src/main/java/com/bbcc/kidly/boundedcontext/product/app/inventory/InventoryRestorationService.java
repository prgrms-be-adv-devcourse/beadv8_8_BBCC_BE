package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductionRecorder;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryRestorer;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRestoration;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 취소됨을 받아 그 주문에서 차감한 재고를 되돌리는 흐름이다. 규칙 판단은 InventoryRestoration이 하고,
 * 서비스는 읽기와 호출 순서만 맡는다. 트랜잭션은 InventoryRestorer와 InventoryDeductionRecorder가 각자 새로 열어서
 * 이 클래스에는 붙이지 않는다. 바깥(리스너) 트랜잭션에서는 쓰기나 잠금을 하지 않아야 한다.
 * 바깥 트랜잭션이 잡은 잠금을 같은 스레드의 안쪽 새 트랜잭션이 기다리면 PostgreSQL이 교착으로 감지하지 못하고 멈추기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRestorationService {

    private final ProductRepository productRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final InventoryRestorer inventoryRestorer;
    private final InventoryDeductionRecorder inventoryDeductionRecorder;

    public void restore(OrderCancelledEvent event) {
        OrderCancelledEvent.Data data = event.data();
        List<InventoryDeduction> records = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data));
        if (records.isEmpty()) {
            // 주문 취소됨이 주문 생성됨보다 먼저 온 경우라, 주문상품마다 FAILED를 남겨 나중에 온 주문 생성됨을 차감하지 않게 한다 (INV-09)
            recordFailed(data);
            return;
        }
        List<InventoryDeduction> targets = InventoryRestoration.of(records).deductedRecords();
        if (targets.isEmpty()) {
            log.info("되돌릴 차감 기록이 없어 건너뜁니다. orderId={}", data.orderId());
            return;
        }
        inventoryRestorer.restore(targets);
    }

    // 취소가 먼저 온 주문의 FAILED 기록을 새 트랜잭션으로 저장한다. 요청을 만들 수 없으면 ERROR 로그만 남긴다
    private void recordFailed(OrderCancelledEvent.Data data) {
        InventoryDeductionRequest request = createRequest(data);
        if (request == null) {
            return;
        }
        inventoryDeductionRecorder.recordFailed(request);
    }

    // 이벤트의 옵션을 읽어 차감 요청을 만든다. 요청을 만들 수 없으면 ERROR 로그를 남기고 null을 돌려준다
    private InventoryDeductionRequest createRequest(OrderCancelledEvent.Data data) {
        List<Long> optionIds = new ArrayList<>();
        for (OrderCancelledEvent.Item item : data.items()) {
            optionIds.add(item.optionId());
        }
        Map<Long, ProductOption> optionById = new HashMap<>();
        for (ProductOption option : productRepository.findOptionsWithProduct(optionIds)) {
            optionById.put(option.getId(), option);
        }

        List<InventoryDeductionRequest.Item> items = new ArrayList<>();
        for (OrderCancelledEvent.Item item : data.items()) {
            ProductOption option = optionById.get(item.optionId());
            if (option == null) {
                // 옵션은 지우지 않고 보관하므로 없는 옵션 ID는 도메인 사이 버그다. 다시 받아도 같으므로 재시도하지 않는다
                log.error("없는 옵션이 든 주문 취소됨이라 처리하지 않습니다. orderId={}, optionId={}",
                    data.orderId(), item.optionId());
                return null;
            }
            items.add(new InventoryDeductionRequest.Item(item.orderItemId(), option, item.quantity()));
        }
        try {
            return InventoryDeductionRequest.of(data.orderId(), items);
        } catch (IllegalArgumentException e) {
            // 주문상품 ID가 겹치는 등 주문 쪽 오류라 요청을 만들 수 없다. 다시 받아도 같으므로 재시도하지 않는다
            log.error("차감 요청을 만들 수 없는 주문 취소됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return null;
        }
    }

    private List<Long> orderItemIds(OrderCancelledEvent.Data data) {
        List<Long> orderItemIds = new ArrayList<>();
        for (OrderCancelledEvent.Item item : data.items()) {
            orderItemIds.add(item.orderItemId());
        }
        return orderItemIds;
    }
}
