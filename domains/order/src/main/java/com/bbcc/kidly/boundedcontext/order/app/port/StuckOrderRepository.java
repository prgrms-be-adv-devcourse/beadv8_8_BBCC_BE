package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 멈춘 주문 조회(#445). 종류마다 "저장된 시각 < before"인 대상을 저장된 시각 · 대상 ID 오름차순으로 읽는다.
 * before = 지금 − 기준 시간이라 저장된 시각 + 기준 시간 < 지금(정확히 같으면 빠짐)과 같다. 구현은 out/persistence.
 * 발송 지연(SHIPPING_DELAYED)은 배송 컨텍스트가 내준다(OrderDeliveries, #617-2 · #641).
 */
public interface StuckOrderRepository {

    /**
     * 목록 한 줄. 지연 로딩 없이 필요한 값만 읽는다.
     */
    interface StuckRow {

        Long getOrderId();

        Long getTargetId();

        LocalDateTime getBasisAt();
    }

    /**
     * 결제대기 · 결제 요청 전 주문. 저장된 시각 = 주문 시각.
     */
    Page<StuckRow> findStockCheckStale(LocalDateTime before, Pageable pageable);

    long countStockCheckStale(LocalDateTime before);

    /**
     * 결제대기 · 결제 마감 지난 주문. 결제 반영 실패 기록이 PENDING인 주문은 만료 스케줄러가 일부러 건너뛰므로 뺀다.
     */
    Page<StuckRow> findPaymentExpiryMissed(LocalDateTime before, Pageable pageable);

    long countPaymentExpiryMissed(LocalDateTime before);

    /**
     * 결제완료인데 배송이 0건인 주문. 저장된 시각 = 결제 시각.
     */
    Page<StuckRow> findPaidWithoutDelivery(LocalDateTime before, Pageable pageable);

    long countPaidWithoutDelivery(LocalDateTime before);

    /**
     * PENDING 결제 반영 실패 기록(주문이 취소된 기록은 뺌). 저장된 시각 = 처음 기록된 시각.
     */
    Page<StuckRow> findRecoveryPendingStale(LocalDateTime before, Pageable pageable);

    long countRecoveryPendingStale(LocalDateTime before);

    /**
     * 배송 완료됐는데 확정 안 된 주문상품(주문이 취소된 것은 뺌). 저장된 시각 = 배송 완료 시각.
     */
    Page<StuckRow> findAutoConfirmMissed(LocalDateTime before, Pageable pageable);

    long countAutoConfirmMissed(LocalDateTime before);

    /**
     * 보내지 못한 주문 이벤트 아웃박스 메시지 한 줄(#602).
     */
    interface OutboxHeadRow {

        Long getId();

        UUID getEventId();

        String getEventType();

        String getMessageKey();

        int getAttemptCount();

        String getLastError();

        LocalDateTime getCreatedAt();

        /**
         * 마지막으로 바뀐 시각 = 마지막 전송 시도 시각(시도 전이면 만든 시각).
         */
        LocalDateTime getUpdatedAt();
    }

    /**
     * 보내지 못한 메시지 중 맨 앞(id가 가장 작은 것). 릴레이는 id 순으로 보내다 처음 실패한 메시지에서 멈추므로 막힌 메시지다.
     */
    Optional<OutboxHeadRow> findOldestUnsentOutbox();

    long countUnsentOutbox();
}
