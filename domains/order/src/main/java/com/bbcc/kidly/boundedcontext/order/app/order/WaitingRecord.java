package com.bbcc.kidly.boundedcontext.order.app.order;

import java.util.Objects;

/**
 * 주문을 취소하는 쪽이 지금 직접 다루고 있는 재처리 대기 기록(#598). 주문 취소가 그 주문의 대기 기록을 닫을 때 이 기록은 빼고,
 * 다루는 쪽(관리자 이벤트 처리 실패 재처리 · 결제 반영 취소)이 원래대로 결과를 정한다.
 */
public record WaitingRecord(Kind kind, Long id) {

    public enum Kind {
        EVENT_FAILURE,
        PAYMENT_RECOVERY
    }

    public static WaitingRecord eventFailure(Long id) {
        return new WaitingRecord(Kind.EVENT_FAILURE, id);
    }

    public static WaitingRecord paymentRecovery(Long id) {
        return new WaitingRecord(Kind.PAYMENT_RECOVERY, id);
    }

    boolean is(Kind kind, Long id) {
        return this.kind == kind && Objects.equals(this.id, id);
    }
}
