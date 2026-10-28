package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 멈춘 주문 조회(#445). 종류마다 "저장된 시각 < before"인 대상을 저장된 시각 · 대상 ID 오름차순으로 읽는다.
 * before = 지금 − 기준 시간이라 저장된 시각 + 기준 시간 < 지금(정확히 같으면 빠짐)과 같다. 구현은 out/persistence.
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
     * 발송 전(READY) 배송. 저장된 시각 = 배송 생성 시각.
     */
    Page<StuckRow> findShippingDelayed(LocalDateTime before, Pageable pageable);

    long countShippingDelayed(LocalDateTime before);

    /**
     * 배송 완료됐는데 확정 안 된 주문상품(주문이 취소된 것은 뺌). 저장된 시각 = 배송 완료 시각.
     */
    Page<StuckRow> findAutoConfirmMissed(LocalDateTime before, Pageable pageable);

    long countAutoConfirmMissed(LocalDateTime before);
}
