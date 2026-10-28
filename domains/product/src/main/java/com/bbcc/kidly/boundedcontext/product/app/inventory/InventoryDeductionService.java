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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 주문 생성됨을 받아 주문상품마다 재고를 차감하는 흐름이다. 규칙 판단은 InventoryDeductionRequest가 하고,
 * 서비스는 읽기와 호출 순서만 맡는다. 차감을 거절하면 재고 부족함을 보낸다.
 * 트랜잭션은 InventoryDeductor와 InventoryDeductionRecorder가 각자 새로 열어서 이 클래스에는 붙이지 않는다.
 * 바깥(리스너) 트랜잭션에서는 쓰기나 잠금을 하지 않아야 한다.
 * 바깥 트랜잭션이 잡은 잠금을 같은 스레드의 안쪽 새 트랜잭션이 기다리면 PostgreSQL이 교착으로 감지하지 못하고 멈추기 때문이다.
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
        InventoryDeductionRequest request = createRequest(data);
        if (request == null || shouldSkip(request, data)) {
            return;
        }
        // 판매중이 아닌 옵션의 주문은 재고를 줄이지 않아야 하므로, 차감하기 전에 판단하고 재고 부족함을 보낸다
        if (!request.isPurchasable()) {
            recordRejected(request, request.notOnSaleRejection(), data);
            return;
        }
        try {
            inventoryDeductor.deduct(request);
        } catch (InventoryShortageException e) {
            // 차감 트랜잭션은 이미 롤백됐다. 주문상품마다 FAILED 기록을 새 트랜잭션으로 남기고 재고 부족함을 보낸다
            recordRejected(request, request.shortageRejection(e.shortOptionIds()), data);
        } catch (DataIntegrityViolationException e) {
            // 차감도 함께 롤백됐으므로 다시 시도하지 않는다
            logIntegrityViolation(e, data);
        }
    }

    // 새 트랜잭션에서 FAILED 기록을 저장하고 재고 부족함을 보낸다. 같은 이벤트를 다른 쪽이 먼저 처리했으면 기록이 UNIQUE 제약에 막히고,
    // 그러면 재고 부족함도 나가지 않는다
    private void recordRejected(
        InventoryDeductionRequest request, InventoryDeductionRejection rejection, OrderCreatedEvent.Data data) {
        try {
            inventoryDeductionRecorder.recordRejected(request, rejection);
        } catch (DataIntegrityViolationException e) {
            logIntegrityViolation(e, data);
        }
    }

    // Spring은 UNIQUE 위반을 FK나 NOT NULL 위반과 같은 타입으로 바꾸므로, 기록이 실제로 있는지 다시 읽어 원인을 가린다
    // 기록이 있으면 같은 주문 생성됨을 다른 쪽이 먼저 처리한 것이다. 없으면 다른 제약 위반이라 ERROR로 남긴다
    private void logIntegrityViolation(DataIntegrityViolationException e, OrderCreatedEvent.Data data) {
        if (!inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data)).isEmpty()) {
            log.info("같은 주문 생성됨을 동시에 처리해 늦은 쪽을 건너뜁니다. orderId={}", data.orderId());
            return;
        }
        log.error("차감 기록 저장이 제약을 어겨 처리하지 않습니다. orderId={}", data.orderId(), e);
    }

    // 이벤트의 옵션을 읽어 차감 요청을 만든다. 요청을 만들 수 없으면 ERROR 로그를 남기고 null을 돌려준다
    private InventoryDeductionRequest createRequest(OrderCreatedEvent.Data data) {
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
            ProductOption option = optionById.get(item.optionId());
            if (option == null) {
                // 옵션은 지우지 않고 보관하므로 없는 옵션 ID는 도메인 사이 버그다. 다시 받아도 같으므로 재시도하지 않는다
                log.error("없는 옵션이 든 주문 생성됨이라 처리하지 않습니다. orderId={}, optionId={}",
                    data.orderId(), item.optionId());
                return null;
            }
            items.add(new InventoryDeductionRequest.Item(item.orderItemId(), option, item.quantity()));
        }
        try {
            return InventoryDeductionRequest.of(data.orderId(), items);
        } catch (IllegalArgumentException e) {
            // 주문상품 ID가 겹치는 등 주문 쪽 오류라 차감 요청을 만들 수 없다. 다시 받아도 같으므로 재시도하지 않는다
            log.error("차감 요청을 만들 수 없는 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return null;
        }
    }

    // 이미 처리한 주문이거나 기존 기록과 어긋나는 주문이면 로그를 남기고 true를 돌려준다
    private boolean shouldSkip(InventoryDeductionRequest request, OrderCreatedEvent.Data data) {
        List<InventoryDeduction> existing = inventoryDeductionRepository.findAllByOrderItemIdIn(orderItemIds(data));
        boolean alreadyHandled;
        try {
            alreadyHandled = request.isAlreadyHandled(existing);
        } catch (IllegalArgumentException e) {
            // 같은 주문상품 ID에 다른 내용이 온 주문 쪽 오류다. 다시 받아도 결과가 같으므로 재시도하지 않는다
            log.error("기존 차감 기록과 다른 주문 생성됨이라 처리하지 않습니다. orderId={}", data.orderId(), e);
            return true;
        }
        // 같은 주문 생성됨을 다시 받았으면 재고도 기록도 바꾸지 않는다
        if (alreadyHandled) {
            log.info("이미 처리한 주문 생성됨이라 건너뜁니다. orderId={}", data.orderId());
        }
        return alreadyHandled;
    }

    private List<Long> orderItemIds(OrderCreatedEvent.Data data) {
        List<Long> orderItemIds = new ArrayList<>();
        for (OrderCreatedEvent.Item item : data.items()) {
            orderItemIds.add(item.orderItemId());
        }
        return orderItemIds;
    }
}
