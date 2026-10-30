package com.bbcc.kidly.boundedcontext.order.app.stuck;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 멈춘 주문 조회(#445). 조회만 하고 상태 · 이력은 바꾸지 않는다.
 * 아웃박스 멈춤(#602)은 보내지 못한 맨 앞 메시지 하나만 본다(릴레이가 처음 실패에서 멈추므로 막힌 메시지는 하나): 시도 횟수가
 * 기준 이상이거나 만든 시각 + 기준 시간이 now보다 이전이면 1건. 보내지면 맨 앞이 바뀌어 저절로 빠진다.
 * 기준 시각(since) = 저장된 시각 + 기준 시간이 now보다 이전이면(같으면 빠짐) 멈춘 것으로 본다.
 * now는 부른 쪽이 한 요청에 한 번 정해 넘긴다(건수 요약의 여섯 종류가 같은 시각으로 센다).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StuckOrderService {

    private final StuckOrderRepository stuckOrderRepository;
    private final StuckOrderThresholds thresholds;
    private final Order.Policy orderPolicy;

    /**
     * 종류별 건수. 모든 종류를 선언 순서로 담고 0도 남긴다. 한 주문이 여러 종류에 들 수 있어 합은 멈춘 주문 수가 아니다.
     * 아웃박스 멈춤은 0 또는 1이다.
     */
    public Map<StuckOrderKind, Long> getSummary(LocalDateTime now) {
        Map<StuckOrderKind, Long> summary = new LinkedHashMap<>();
        Arrays.stream(StuckOrderKind.values()).forEach(kind -> summary.put(kind, kind == StuckOrderKind.OUTBOX_STUCK
            ? stuckOutbox(now).map(head -> 1L).orElse(0L)
            : count(kind, now.minus(waitOf(kind)))));
        return summary;
    }

    /**
     * 그 종류의 멈춘 대상. 기준 시각 오름차순, 같으면 대상 ID 오름차순.
     *
     * @param kindName 종류 이름. 대문자 그대로만 받는다(아니면 INVALID_STUCK_KIND)
     */
    public Page<StuckOrderResult> getStuckOrders(String kindName, LocalDateTime now, int page, int size) {
        StuckOrderKind kind = toKind(kindName);
        if (kind == StuckOrderKind.OUTBOX_STUCK) {
            return getStuckOutbox(now, PageRequest.of(page, size));
        }
        Duration wait = waitOf(kind);
        LocalDateTime before = now.minus(wait);
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, count(kind, before));
        }
        return find(kind, before, pageable).map(row -> new StuckOrderResult(kind.name(), row.getOrderId(),
            kind.targetType(), row.getTargetId(), kind.status(), row.getBasisAt().plus(wait)));
    }

    private Duration waitOf(StuckOrderKind kind) {
        return switch (kind) {
            case STOCK_CHECK_STALE -> thresholds.stockWait();
            case PAYMENT_EXPIRY_MISSED -> thresholds.expiryGrace();
            case PAID_WITHOUT_DELIVERY -> thresholds.deliveryWait();
            case RECOVERY_PENDING_STALE -> thresholds.recoveryWait();
            case SHIPPING_DELAYED -> thresholds.shippingWait();
            case AUTO_CONFIRM_MISSED -> orderPolicy.autoConfirmAfter().plus(thresholds.autoConfirmGrace());
            case OUTBOX_STUCK -> throw outboxNotPerOrder();
        };
    }

    private Page<StuckOrderRepository.StuckRow> find(StuckOrderKind kind, LocalDateTime before, PageRequest pageable) {
        return switch (kind) {
            case STOCK_CHECK_STALE -> stuckOrderRepository.findStockCheckStale(before, pageable);
            case PAYMENT_EXPIRY_MISSED -> stuckOrderRepository.findPaymentExpiryMissed(before, pageable);
            case PAID_WITHOUT_DELIVERY -> stuckOrderRepository.findPaidWithoutDelivery(before, pageable);
            case RECOVERY_PENDING_STALE -> stuckOrderRepository.findRecoveryPendingStale(before, pageable);
            case SHIPPING_DELAYED -> stuckOrderRepository.findShippingDelayed(before, pageable);
            case AUTO_CONFIRM_MISSED -> stuckOrderRepository.findAutoConfirmMissed(before, pageable);
            case OUTBOX_STUCK -> throw outboxNotPerOrder();
        };
    }

    private long count(StuckOrderKind kind, LocalDateTime before) {
        return switch (kind) {
            case STOCK_CHECK_STALE -> stuckOrderRepository.countStockCheckStale(before);
            case PAYMENT_EXPIRY_MISSED -> stuckOrderRepository.countPaymentExpiryMissed(before);
            case PAID_WITHOUT_DELIVERY -> stuckOrderRepository.countPaidWithoutDelivery(before);
            case RECOVERY_PENDING_STALE -> stuckOrderRepository.countRecoveryPendingStale(before);
            case SHIPPING_DELAYED -> stuckOrderRepository.countShippingDelayed(before);
            case AUTO_CONFIRM_MISSED -> stuckOrderRepository.countAutoConfirmMissed(before);
            case OUTBOX_STUCK -> throw outboxNotPerOrder();
        };
    }

    // 멈춘 메시지는 하나뿐이라 0 · 1건이다. 둘째 페이지부터는 빈 목록(전체 건수는 그대로)
    private Page<StuckOrderResult> getStuckOutbox(LocalDateTime now, PageRequest pageable) {
        Optional<StuckOrderRepository.OutboxHeadRow> stuck = stuckOutbox(now);
        if (stuck.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        if (pageable.getOffset() > 0) {
            return new PageImpl<>(List.of(), pageable, 1);
        }
        StuckOrderRepository.OutboxHeadRow head = stuck.get();
        long backlog = stuckOrderRepository.countUnsentOutbox() - 1;
        StuckOrderKind kind = StuckOrderKind.OUTBOX_STUCK;
        return new PageImpl<>(List.of(new StuckOrderResult(kind.name(), orderIdOf(head.getMessageKey()),
            kind.targetType(), head.getId(), kind.status(), outboxSince(head, now),
            new StuckOrderResult.Outbox(head.getId(), head.getEventType(), head.getEventId(), head.getAttemptCount(),
                head.getCreatedAt(), head.getLastError(), backlog))), pageable, 1);
    }

    // 맨 앞 미전송 메시지가 기준(시도 횟수 · 만든 뒤 시간)을 넘었으면 그 메시지
    private Optional<StuckOrderRepository.OutboxHeadRow> stuckOutbox(LocalDateTime now) {
        return stuckOrderRepository.findOldestUnsentOutbox()
            .filter(head -> head.getAttemptCount() >= thresholds.outboxAttempts()
                || outboxAgedAt(head).isBefore(now));
    }

    // 시간 기준을 넘었으면 만든 시각 + 기준 시간, 시도 횟수 기준만 넘었으면 마지막 시도 시각
    private LocalDateTime outboxSince(StuckOrderRepository.OutboxHeadRow head, LocalDateTime now) {
        LocalDateTime agedAt = outboxAgedAt(head);
        return agedAt.isBefore(now) ? agedAt : head.getUpdatedAt();
    }

    private LocalDateTime outboxAgedAt(StuckOrderRepository.OutboxHeadRow head) {
        return head.getCreatedAt().plus(thresholds.outboxWait());
    }

    // 주문 이벤트의 키는 주문 ID(correlationId)다. 숫자가 아니면(계약 밖) 주문을 모른다
    private static Long orderIdOf(String messageKey) {
        try {
            return Long.valueOf(messageKey);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 아웃박스 멈춤은 주문 하나가 아니라 맨 앞 메시지를 보므로 기준 시간 · 목록 · 건수를 따로 구한다
    private static IllegalStateException outboxNotPerOrder() {
        return new IllegalStateException("OUTBOX_STUCK은 맨 앞 미전송 메시지로 따로 구한다");
    }

    // 이름이 정확히 같아야 한다(없음 · 소문자 · 모르는 값은 400). in은 이 enum을 몰라도 되게 이름으로 받는다
    private static StuckOrderKind toKind(String kindName) {
        return Arrays.stream(StuckOrderKind.values())
            .filter(kind -> kind.name().equals(kindName))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_STUCK_KIND));
    }
}
