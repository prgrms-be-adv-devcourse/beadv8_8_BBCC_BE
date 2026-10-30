package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.expiry.PaymentEventLagCheck;

/**
 * 주문이 아직 처리하지 않은 결제 이벤트가 남아 있는지 확인한다(#561). 구현은 out/messaging(Kafka 구독 그룹 kidly.order의
 * kidly.payment.events 커밋 위치 vs 끝 위치). 결제 마감 만료가 처리 전 payment.completed가 남은 주문을 취소하지 않게 쓴다.
 * 예외를 던지지 않고, 확인하지 못하면(시간 초과 · 무응답) CHECK_FAILED를 돌려준다.
 */
public interface PaymentEventLag {

    PaymentEventLagCheck check();
}
