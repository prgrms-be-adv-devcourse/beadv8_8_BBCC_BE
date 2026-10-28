package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.domain.recovery.OrderEventFailure;

/**
 * 받은 이벤트 처리 실패 보관(#449)이 실패한 이유를 나눈다. 진 쪽 · 실패 코드 · 기록 잠김은 결제 완료와 같은
 * PaymentFailures 기준을 쓰고, 기록 유니크만 다르다.
 */
public final class OrderEventFailures {

    private OrderEventFailures() {
    }

    /**
     * 같은 주문 · 같은 종류의 이벤트 두 건이 동시에 실패하면 늦은 쪽 기록 저장이 기록 유니크에 걸린다 = 기록은 이미 1건 있다.
     */
    public static boolean isAlreadyRecorded(Throwable recordError) {
        return PaymentFailures.violates(recordError, OrderEventFailure.ORDER_EVENT_UNIQUE);
    }
}
