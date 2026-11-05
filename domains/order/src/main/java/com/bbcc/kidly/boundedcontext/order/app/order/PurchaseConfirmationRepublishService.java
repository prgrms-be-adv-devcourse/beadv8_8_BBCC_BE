package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.port.ConfirmedPurchases;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전달 이력/Kafka가 만료돼도 주문의 확정 사실을 기존 계약으로 다시 발행한다. 한 요청은 최대 100건이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseConfirmationRepublishService {

    private final ConfirmedPurchases purchases;
    private final EventPublisher events;
    private final OrderEventOutbox outbox;

    @Transactional
    public Result republish(long afterId, Long throughId, int size, Long adminId) {
        long upper = throughId == null ? purchases.upperBound() : throughId;
        if (afterId < 0 || upper < afterId || size < 1 || size > 100) {
            throw new BusinessException(OrderErrorCode.INVALID_CONFIRMATION_REPLAY_RANGE);
        }
        List<ConfirmedPurchases.Purchase> rows = purchases.findAfter(afterId, upper, Limit.of(size + 1));
        List<ConfirmedPurchases.Purchase> batch = rows.stream().limit(size).toList();
        for (var row : batch) {
            var event = new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), row.getConfirmedAt(),
                new OrderItemPurchaseConfirmedEvent.Data(row.getOrderItemId(), row.getOrderId(), row.getMemberId(),
                    row.getSellerId(), row.getChildId(), row.getProductId(), row.getOptionId(), row.getSize(),
                    row.getQuantity(), row.getAmount(), row.getConfirmedAt()));
            events.publish(event);
            outbox.append(event);
        }
        boolean hasNext = rows.size() > size;
        Long next = hasNext ? batch.getLast().getOrderItemId() : null;
        log.info("구매확정 사실 재발행: adminId={} afterId={} throughId={} published={} hasNext={}",
            adminId, afterId, upper, batch.size(), hasNext);
        return new Result(batch.size(), upper, next, hasNext);
    }

    public record Result(int published, long throughOrderItemId, Long nextAfterOrderItemId, boolean hasNext) {
    }
}
