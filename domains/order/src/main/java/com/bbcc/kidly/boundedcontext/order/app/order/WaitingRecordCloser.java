package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderActor;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventFailureRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderStatusHistoryRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.PaymentRecoveryRepository;
import com.bbcc.kidly.boundedcontext.order.domain.history.OrderStatusHistory;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주문이 취소되면 그 주문의 재처리 대기(PENDING) 기록을 같은 트랜잭션에서 닫는다(#598). 주문 취소가 모이는 한 곳(OrderService가
 * order.cancelled를 발행할 때)에서 불러, 새 취소 경로가 생겨도 빠지지 않는다. 관리자 화면에 "주문은 취소됐는데 재처리 대기"로 남지 않는다.
 * <ul>
 *   <li>이벤트 처리 실패 · 결제 반영 실패 기록을 CANCELLED로, 이유는 실패 코드 칸에 ORDER_CANCELLED. 결제 반영 실패는 다른 상태 변경처럼
 *       상태 이력 한 줄(주체 = 취소 주체). 빠진 돈은 같은 트랜잭션의 order.cancelled를 받은 결제 쪽이 환불한다</li>
 *   <li>다른 트랜잭션(관리자 재처리 · 종료)이 잡고 있는 기록은 기다리지 않고 건너뛴다(SKIP LOCKED). 그 동작이 주문 취소를 보고 닫는다</li>
 *   <li>취소하는 쪽이 지금 다루는 기록(handled)은 빼고, 그쪽이 원래대로 결과를 정한다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
class WaitingRecordCloser {

    private final OrderEventFailureRepository failureRepository;
    private final PaymentRecoveryRepository recoveryRepository;
    private final OrderStatusHistoryRepository historyRepository;

    /**
     * @param handled 취소하는 쪽이 지금 다루는 기록. 없으면 null
     */
    void closeFor(Long orderId, OrderActor actor, WaitingRecord handled, LocalDateTime now) {
        List<Long> failureIds = new ArrayList<>();
        for (OrderEventFailure failure : failureRepository.findByOrderIdAndStatusForUpdateSkipLocked(orderId,
            OrderEventFailure.Status.PENDING)) {
            if (!isHandled(handled, WaitingRecord.Kind.EVENT_FAILURE, failure.getId())) {
                failure.closeForOrderCancelled(now);
                failureIds.add(failure.getId());
            }
        }
        Long recoveryId = recoveryRepository
            .findByOrderIdAndStatusForUpdateSkipLocked(orderId, PaymentRecovery.Status.PENDING)
            .filter(recovery -> !isHandled(handled, WaitingRecord.Kind.PAYMENT_RECOVERY, recovery.getId()))
            .map(recovery -> {
                recovery.closeForOrderCancelled(now);
                historyRepository.save(actor.history(orderId, OrderStatusHistory.TargetType.PAYMENT_RECOVERY,
                    recovery.getId(), PaymentRecovery.Status.PENDING, recovery.getStatus(),
                    OrderEventFailure.ORDER_CANCELLED, now));
                return recovery.getId();
            })
            .orElse(null);
        if (!failureIds.isEmpty() || recoveryId != null) {
            log.info("주문 취소로 재처리 대기 기록 닫음: orderId={} eventFailureIds={} paymentRecoveryId={}",
                orderId, failureIds, recoveryId);
        }
    }

    private static boolean isHandled(WaitingRecord handled, WaitingRecord.Kind kind, Long id) {
        return handled != null && handled.is(kind, id);
    }
}
