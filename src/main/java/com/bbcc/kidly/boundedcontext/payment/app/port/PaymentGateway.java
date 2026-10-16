package com.bbcc.kidly.boundedcontext.payment.app.port;

/**
 * 결제대행사(PG) 연결. 서비스는 토스 응답 형식을 모르고 이 결과만 본다.
 * 승인·취소는 같은 멱등 키로 다시 호출해도 PG에서 한 번만 처리된다.
 */
public interface PaymentGateway {

    // 부분 취소된 결제의 상태. 전액 취소 조회의 거절 코드로 온다
    String PARTIAL_CANCELED = "PARTIAL_CANCELED";

    PgResult<PgApproval> confirm(String paymentKey, String orderId, long amount, String idempotencyKey);

    PgResult<PgCancellation> cancel(String paymentKey, String reason, String idempotencyKey);

    /**
     * 승인 결과를 모를 때(시간 초과 등) PG의 실제 상태를 확인한다.
     * 승인됐으면 성공, 승인되지 않고 끝났거나 PG에 결제가 없으면 거절, 아직 진행 중이거나 조회가 실패하면 모름을 돌려준다.
     */
    PgResult<PgApproval> findByOrderId(String orderId);

    /**
     * PG에서 직접 취소된 결제를 확인한다. 전액 취소됐으면 성공, 아니면 거절(코드는 PG 결제 상태), 조회가 실패하면 모름을 돌려준다.
     */
    PgResult<PgCancellation> findCancellationByOrderId(String orderId);
}
