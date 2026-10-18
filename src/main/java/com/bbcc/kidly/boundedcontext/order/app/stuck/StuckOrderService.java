package com.bbcc.kidly.boundedcontext.order.app.stuck;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.stuck.dto.StuckOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 멈춘 주문 조회(#445). 조회만 하고 상태 · 이력은 바꾸지 않는다.
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
     * 종류별 건수. 여섯 종류 모두 선언 순서로 담고 0도 남긴다. 한 주문이 여러 종류에 들 수 있어 합은 멈춘 주문 수가 아니다.
     */
    public Map<StuckOrderKind, Long> getSummary(LocalDateTime now) {
        Map<StuckOrderKind, Long> summary = new LinkedHashMap<>();
        Arrays.stream(StuckOrderKind.values()).forEach(kind -> summary.put(kind, count(kind, now.minus(waitOf(kind)))));
        return summary;
    }

    /**
     * 그 종류의 멈춘 대상. 기준 시각 오름차순, 같으면 대상 ID 오름차순.
     *
     * @param kindName 종류 이름. 대문자 그대로만 받는다(아니면 INVALID_STUCK_KIND)
     */
    public Page<StuckOrderResult> getStuckOrders(String kindName, LocalDateTime now, int page, int size) {
        StuckOrderKind kind = toKind(kindName);
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
        };
    }

    // 이름이 정확히 같아야 한다(없음 · 소문자 · 모르는 값은 400). in은 이 enum을 몰라도 되게 이름으로 받는다
    private static StuckOrderKind toKind(String kindName) {
        return Arrays.stream(StuckOrderKind.values())
            .filter(kind -> kind.name().equals(kindName))
            .findFirst()
            .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_STUCK_KIND));
    }
}
