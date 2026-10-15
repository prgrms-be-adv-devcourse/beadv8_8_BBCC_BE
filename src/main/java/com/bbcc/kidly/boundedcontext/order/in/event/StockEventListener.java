package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.StockDeductionFailedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * stock.deducted를 받아 그 주문의 결제를 요청하고, stock.deduction-failed를 받아 재고 부족 취소를 맡긴다.
 * 이벤트 필드가 바뀌면 여기 변환만 고친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final OrderService orderService;

    /**
     * 주문 ID가 없으면 찾을 주문이 없으므로 WARN만 남기고 무시한다.
     */
    @DomainEventListener
    public void onStockDeducted(StockDeductedEvent event) {
        Long orderId = toOrderId(event);
        if (orderId == null) {
            log.warn("재고 이벤트 무시: event=stock.deducted reason=missing-order-id");
            return;
        }
        orderService.requestPayment(orderId);
    }

    /**
     * 재고 차감 실패를 받아 재고 부족 취소를 맡긴다. 대표 사유(data.reason)는 쓰지 않고 INFO 로그에만 남긴다.
     * 주문 ID가 없으면 무시(WARN). 주문상품 목록이 없으면 빈 목록, 빈 줄 · 주문상품 ID 없는 줄 · 사유 없는 줄은
     * 그 줄만 건너뛴다(WARN). 같은 주문상품 ID가 두 번 오면 마지막 사유를 쓴다(WARN).
     */
    @DomainEventListener
    public void onStockDeductionFailed(StockDeductionFailedEvent event) {
        StockDeductionFailedEvent.Data data = event.data();
        if (data == null || data.orderId() == null) {
            log.warn("재고 이벤트 무시: event=stock.deduction-failed reason=missing-order-id");
            return;
        }
        log.info("재고 차감 실패 수신: event=stock.deduction-failed orderId={} reason={}", data.orderId(), data.reason());
        orderService.cancelForStockDeductionFailure(
            new StockDeductionFailedCommand(data.orderId(), toItems(data.orderId(), data.failures())));
    }

    private static List<StockDeductionFailedCommand.Item> toItems(Long orderId,
        List<StockDeductionFailedEvent.Failure> failures) {
        if (failures == null) {
            return List.of();
        }
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (StockDeductionFailedEvent.Failure failure : failures) {
            if (failure == null || failure.orderItemId() == null) {
                log.warn("재고 차감 실패 줄 무시: event=stock.deduction-failed reason=missing-order-item-id orderId={}",
                    orderId);
                continue;
            }
            if (failure.reason() == null) {
                log.warn("재고 차감 실패 줄 무시: event=stock.deduction-failed reason=missing-failure-reason orderId={} "
                    + "orderItemId={}", orderId, failure.orderItemId());
                continue;
            }
            if (reasons.containsKey(failure.orderItemId())) {
                log.warn("재고 차감 실패 줄 중복: event=stock.deduction-failed reason=duplicate-order-item-id orderId={} "
                    + "orderItemId={}", orderId, failure.orderItemId());
            }
            reasons.put(failure.orderItemId(), failure.reason().name());
        }
        return reasons.entrySet().stream()
            .map(entry -> new StockDeductionFailedCommand.Item(entry.getKey(), entry.getValue()))
            .toList();
    }

    private Long toOrderId(StockDeductedEvent event) {
        return event.data() == null ? null : event.data().orderId();
    }
}
