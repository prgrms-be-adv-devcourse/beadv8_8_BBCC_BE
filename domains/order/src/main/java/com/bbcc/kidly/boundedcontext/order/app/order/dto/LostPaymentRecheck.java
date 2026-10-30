package com.bbcc.kidly.boundedcontext.order.app.order.dto;

/**
 * 동시 처리에서 진 결제 완료를 새 트랜잭션에서 다시 읽은 결과(#519 · #594).
 */
public enum LostPaymentRecheck {
    /** 이긴 쪽이 주문을 취소했다(돈은 빠졌는데 취소됨) — 환불 확인 WARN을 남겼다 */
    PAID_AFTER_CANCEL,
    /** 결제완료 · 이 결제로 반영 뒤 취소 · 주문 없음 등 결과가 이미 났다 — 다시 처리해도 아무것도 안 한다 */
    SETTLED,
    /** 아직 결제대기다 — 반영이 통째로 되돌려졌으므로(예: 장바구니 줄 동시 변경) 다시 처리해야 한다 */
    STILL_PENDING
}
