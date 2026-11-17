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
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRestorationService {

    private static final String ORDER_CANCELLED = "주문 취소됨";
    private static final String ORDER_ITEM_CANCELLED = "주문상품 취소됨";

    private final ProductRepository productRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final InventoryRestorer inventoryRestorer;
    private final InventoryDeductionRecorder inventoryDeductionRecorder;

    public void restore(OrderCancelledEvent event) {
        OrderCancelledEvent.Data data = event.data();
        InventoryRestoration restoration = readRestoration(data.orderId(), orderItemIds(data), ORDER_CANCELLED);
        if (restoration != null && restoration.isCancelledBeforeCreated()) {
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

    public void restore(OrderItemCancelledEvent event) {
        OrderItemCancelledEvent.Data data = event.data();
        List<Long> orderItemIds = orderItemIds(data);
        InventoryRestoration restoration = readRestoration(data.orderId(), orderItemIds, ORDER_ITEM_CANCELLED);
        if (restoration == null) {
            return;
        }
        if (restoration.isCancelledBeforeCreated()) {
            log.error("차감 기록이 없는 주문상품 취소됨이라 처리하지 않습니다. orderId={}, cancellationId={}",
                data.orderId(), data.cancellationId());
            return;
        }
        List<Long> missing = restoration.orderItemIdsWithoutRecord(orderItemIds);
        if (!missing.isEmpty()) {
            log.error("차감 기록이 없는 주문상품이 든 주문상품 취소됨이라 그 주문상품은 되돌리지 않습니다. "
                + "orderId={}, cancellationId={}, orderItemIds={}", data.orderId(), data.cancellationId(), missing);
        }
        if (restoration.isRejected()) {
            log.error("거절한 주문상품의 주문상품 취소됨이라 처리하지 않습니다. orderId={}, cancellationId={}",
                data.orderId(), data.cancellationId());
            return;
        }
        List<InventoryDeduction> targets = restoration.recordsToRestore();
        if (targets.isEmpty()) {
            log.info("이미 되돌린 주문상품 취소됨이라 건너뜁니다. orderId={}, cancellationId={}",
                data.orderId(), data.cancellationId());
            return;
        }
        inventoryRestorer.restore(targets);
    }

    private InventoryRestoration readRestoration(Long orderId, List<Long> orderItemIds, String eventName) {
        List<InventoryDeduction> records = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds);
        try {
            return InventoryRestoration.of(orderId, records);
        } catch (IllegalArgumentException e) {
            log.error("다른 주문의 차감 기록이 든 {}이라 처리하지 않습니다. orderId={}", eventName, orderId, e);
            return null;
        }
    }

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
            InventoryRestoration reread = readRestoration(data.orderId(), orderItemIds(data), ORDER_CANCELLED);
            if (reread == null) {
                return null;
            }
            if (reread.isCancelledBeforeCreated()) {
                log.error("FAILED 차감 기록 저장이 제약을 어겨 처리하지 않습니다. orderId={}", data.orderId(), e);
                return null;
            }
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

    private InventoryDeductionRequest createRequest(OrderCancelledEvent.Data data) {
        List<Long> optionIds = data.items().stream().map(OrderCancelledEvent.Item::optionId).toList();
        var optionById = new HashMap<Long, ProductOption>();
        for (ProductOption option : productRepository.findOptionsWithProduct(optionIds)) {
            optionById.put(option.getId(), option);
        }

        var items = new ArrayList<InventoryDeductionRequest.Item>();
        for (OrderCancelledEvent.Item item : data.items()) {
            ProductOption option = optionById.get(item.optionId());
            if (option == null) {
                log.error("없는 옵션이 든 주문 취소됨이라 처리하지 않습니다. orderId={}, optionId={}",
                    data.orderId(), item.optionId());
                return null;
            }
            items.add(new InventoryDeductionRequest.Item(item.orderItemId(), option, item.quantity()));
        }
        try {
            return InventoryDeductionRequest.of(data.orderId(), items);
        } catch (IllegalArgumentException e) {
            log.error("차감 요청을 만들 수 없는 주문 취소됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return null;
        }
    }

    private List<Long> orderItemIds(OrderCancelledEvent.Data data) {
        return data.items().stream().map(OrderCancelledEvent.Item::orderItemId).toList();
    }

    private List<Long> orderItemIds(OrderItemCancelledEvent.Data data) {
        return data.items().stream().map(OrderItemCancelledEvent.Item::orderItemId).toList();
    }
}
