package com.bbcc.kidly.boundedcontext.product.app.inventory;

import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductionRecorder;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryDeductor;
import com.bbcc.kidly.boundedcontext.product.app.inventory.support.InventoryShortageException;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRejection;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRepository;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.shared.order.event.OrderCreatedEvent;
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
public class InventoryDeductionService {

    private final ProductRepository productRepository;
    private final InventoryDeductionRepository inventoryDeductionRepository;
    private final InventoryDeductor inventoryDeductor;
    private final InventoryDeductionRecorder inventoryDeductionRecorder;

    public void deduct(OrderCreatedEvent event) {
        OrderCreatedEvent.Data data = event.data();
        InventoryDeductionRequest request = createRequest(data);
        if (request == null || shouldSkip(request, data)) {
            return;
        }
        if (!request.isPurchasable()) {
            recordRejected(request, request.notOnSaleRejection(), data);
            return;
        }
        try {
            inventoryDeductor.deduct(request);
        } catch (InventoryShortageException e) {
            recordRejected(request, request.shortageRejection(e.shortOptionIds()), data);
        } catch (DataIntegrityViolationException e) {
            logIntegrityViolation(e, data);
        }
    }

    private void recordRejected(
        InventoryDeductionRequest request, InventoryDeductionRejection rejection, OrderCreatedEvent.Data data) {
        try {
            inventoryDeductionRecorder.recordRejected(request, rejection);
        } catch (DataIntegrityViolationException e) {
            logIntegrityViolation(e, data);
        }
    }

    // Spring은 UNIQUE 위반을 FK나 NOT NULL 위반과 같은 타입으로 바꾸므로, 기록이 실제로 있는지 다시 읽어 원인을 가린다
    private void logIntegrityViolation(DataIntegrityViolationException e, OrderCreatedEvent.Data data) {
        if (!inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data)).isEmpty()) {
            log.info("같은 주문 생성됨을 동시에 처리해 늦은 쪽을 건너뜁니다. orderId={}", data.orderId());
            return;
        }
        log.error("차감 기록 저장이 제약을 어겨 처리하지 않습니다. orderId={}", data.orderId(), e);
    }

    private InventoryDeductionRequest createRequest(OrderCreatedEvent.Data data) {
        List<Long> optionIds = data.items().stream().map(OrderCreatedEvent.Item::optionId).toList();
        var optionById = new HashMap<Long, ProductOption>();
        for (ProductOption option : productRepository.findOptionsWithProduct(optionIds)) {
            optionById.put(option.getId(), option);
        }

        var items = new ArrayList<InventoryDeductionRequest.Item>();
        for (OrderCreatedEvent.Item item : data.items()) {
            ProductOption option = optionById.get(item.optionId());
            if (option == null) {
                log.error("없는 옵션이 든 주문 생성됨이라 처리하지 않습니다. orderId={}, optionId={}",
                    data.orderId(), item.optionId());
                return null;
            }
            items.add(new InventoryDeductionRequest.Item(item.orderItemId(), option, item.quantity()));
        }
        try {
            return InventoryDeductionRequest.of(data.orderId(), items);
        } catch (IllegalArgumentException e) {
            log.error("차감 요청을 만들 수 없는 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return null;
        }
    }

    private boolean shouldSkip(InventoryDeductionRequest request, OrderCreatedEvent.Data data) {
        List<InventoryDeduction> existing = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data));
        boolean alreadyHandled;
        try {
            alreadyHandled = request.isAlreadyHandled(existing);
        } catch (IllegalArgumentException e) {
            log.error("기존 차감 기록과 다른 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return true;
        }
        if (alreadyHandled) {
            log.info("이미 처리한 주문 생성됨이라 건너뜁니다. orderId={}", data.orderId());
        }
        return alreadyHandled;
    }

    private List<Long> orderItemIds(OrderCreatedEvent.Data data) {
        return data.items().stream().map(OrderCreatedEvent.Item::orderItemId).toList();
    }
}
