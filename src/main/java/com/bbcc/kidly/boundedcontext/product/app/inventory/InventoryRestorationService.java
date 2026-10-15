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
import org.springframework.dao.DataIntegrityViolationException;
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
        InventoryRestoration restoration = readRestoration(data);
        if (restoration != null && restoration.isCancelledBeforeCreated()) {
            // 주문 취소됨이 주문 생성됨보다 먼저 온 경우라, 주문상품마다 FAILED를 남겨 나중에 온 주문 생성됨을 차감하지 않게 한다 (INV-09)
            restoration = recordFailedOrReread(data);
        }
        if (restoration == null) {
            return;
        }
        List<InventoryDeduction> targets = restoration.recordsToRestore();
        if (targets.isEmpty()) {
            logNothingToRestore(restoration, data);
            return;
        }
        inventoryRestorer.restore(targets);
    }

    // 주문상품의 기존 차감 기록을 읽는다. 다른 주문의 기록이 섞여 있으면 ERROR 로그를 남기고 null을 돌려준다
    private InventoryRestoration readRestoration(OrderCancelledEvent.Data data) {
        List<InventoryDeduction> records = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data));
        try {
            return InventoryRestoration.of(data.orderId(), records);
        } catch (IllegalArgumentException e) {
            // 주문 쪽 오류다. 다시 받아도 결과가 같으므로 재시도하지 않는다
            log.error("다른 주문의 차감 기록이 든 주문 취소됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return null;
        }
    }

    // 취소가 먼저 온 주문의 FAILED 기록을 새 트랜잭션으로 저장한다
    // 저장했거나 더 할 일이 없으면 null을, 그사이 커밋된 기록에 막혔으면 다시 읽은 기록을 돌려준다
    private InventoryRestoration recordFailedOrReread(OrderCancelledEvent.Data data) {
        InventoryDeductionRequest request = createRequest(data);
        if (request == null) {
            return null;
        }
        try {
            inventoryDeductionRecorder.recordFailed(request);
            return null;
        } catch (DataIntegrityViolationException e) {
            // Spring은 UNIQUE 위반을 다른 제약 위반과 같은 타입으로 바꾸므로, 기록을 다시 읽어 원인을 가린다
            InventoryRestoration reread = readRestoration(data);
            if (reread == null) {
                return null;
            }
            if (reread.isCancelledBeforeCreated()) {
                log.error("FAILED 차감 기록 저장이 제약을 어겨 처리하지 않습니다. orderId={}", data.orderId(), e);
                return null;
            }
            // 차감이 먼저 커밋했으면 되돌리고, 같은 취소가 FAILED를 먼저 남겼으면 되돌릴 것이 없다
            log.info("FAILED 차감 기록이 같은 주문상품의 기존 기록에 막혀 그 기록으로 이어 갑니다. orderId={}", data.orderId());
            return reread;
        }
    }

    private void logNothingToRestore(InventoryRestoration restoration, OrderCancelledEvent.Data data) {
        if (restoration.isRejected()) {
            log.warn("거절한 주문의 주문 취소됨이라 되돌릴 재고가 없습니다. orderId={}", data.orderId());
            return;
        }
        log.info("이미 되돌린 주문 취소됨이라 건너뜁니다. orderId={}", data.orderId());
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
