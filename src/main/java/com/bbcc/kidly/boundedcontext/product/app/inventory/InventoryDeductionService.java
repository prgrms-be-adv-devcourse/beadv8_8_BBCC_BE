package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductionRecorder;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductor;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryShortageException;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 주문 생성됨을 받아 주문상품마다 재고를 차감하는 흐름이다. 규칙 판단은 InventoryDeductionRequest가 하고,
 * 서비스는 읽기와 호출 순서만 맡는다. 트랜잭션은 InventoryDeductor가 직접 열어서 이 클래스에는 붙이지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDeductionService {

    private final ProductRepository productRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
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
        InventoryDeductionRequest request;
        try {
            request = InventoryDeductionRequest.of(data.orderId(), items);
        } catch (IllegalArgumentException e) {
            // 주문상품 ID가 겹치는 등 주문 쪽 오류라 차감 요청을 만들 수 없다. 다시 받아도 같으므로 재시도하지 않는다
            log.error("차감 요청을 만들 수 없는 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return;
        }
        boolean alreadyHandled;
        try {
            alreadyHandled = request.isAlreadyHandled(
                inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data)));
        } catch (IllegalArgumentException e) {
            // 같은 주문상품 ID에 다른 내용이 온 주문 쪽 오류다. 다시 받아도 결과가 같으므로 재시도하지 않는다
            log.error("기존 차감 기록과 다른 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return;
        }
        // 같은 주문 생성됨을 다시 받았으면 재고도 기록도 바꾸지 않는다
        if (alreadyHandled) {
            log.info("이미 처리한 주문 생성됨이라 건너뜁니다. orderId={}", data.orderId());
            return;
        }
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

    private List<Long> orderItemIds(OrderCreatedEvent.Data data) {
        List<Long> orderItemIds = new ArrayList<>();
        for (OrderCreatedEvent.Item item : data.items()) {
            orderItemIds.add(item.orderItemId());
        }
        return orderItemIds;
    }
}
