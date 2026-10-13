package com.bbcc.kidly.boundedcontext.payment.app.port;

/**
 * 결제대행사(PG) 연결. 서비스는 토스 응답 형식을 모르고 이 결과만 본다.
 * 승인·취소는 같은 멱등 키로 다시 호출해도 PG에서 한 번만 처리된다.
 */
public interface PaymentGateway {

    PgResult<PgApproval> confirm(String paymentKey, String orderId, long amount, String idempotencyKey);

    PgResult<PgCancellation> cancel(String paymentKey, String reason, String idempotencyKey);

    /**
     * 승인 결과를 모를 때(시간 초과 등) PG의 실제 상태를 확인한다.
     * 승인됐으면 성공, 승인되지 않고 끝났으면 거절, 아직 진행 중이면 모름을 돌려준다.
     */
    PgResult<PgApproval> findByOrderId(String orderId);
}
