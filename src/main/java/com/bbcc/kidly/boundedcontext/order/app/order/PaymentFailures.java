package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import com.bbcc.kidly.boundedcontext.order.domain.recovery.PaymentRecovery;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * 결제 완료 반영 실패를 나눈다(#353). 결제 완료 리스너와 관리자 재처리가 같은 기준을 쓴다.
 * 동시 중복 처리에서 진 쪽(이긴 쪽이 이미 처리함)은 보관하지 않고, 결제 내용 불일치는 재처리할 수 없다.
 */
public final class PaymentFailures {

    private static final String DELIVERY_UNIQUE = "uk_delivery_order_seller";

    private PaymentFailures() {
    }

    /**
     * 낙관적 락 충돌 · 배송 (order_id, seller_id) 유니크 위반 = 동시 중복 처리에서 진 쪽.
     * 제약 이름은 DB 오류 메시지로만 알 수 있어 원인 사슬의 메시지에서 찾는다.
     */
    public static boolean isLostConcurrentDuplicate(Throwable error) {
        if (error instanceof OptimisticLockingFailureException) {
            return true;
        }
        return violates(error, DELIVERY_UNIQUE);
    }

    /**
     * 기록 · 로그에 남길 공백 없는 코드. 결제 불일치는 사유 코드, 그 밖은 예외 클래스 이름.
     */
    public static String failureCode(Throwable error) {
        if (error instanceof PaymentMismatchException mismatch) {
            return mismatch.getReason().code();
        }
        return error.getClass().getSimpleName();
    }

    /**
     * 재처리 · 취소가 기록을 잠그고 있어 기록 저장이 기다리지 않고 바로 실패했다(#353). 기록은 있고 재처리 · 취소가 처리 중이다.
     */
    public static boolean isRecordLocked(Throwable recordError) {
        return recordError instanceof PessimisticLockingFailureException;
    }

    /**
     * 같은 주문의 결제 완료 두 건이 동시에 실패하면 늦은 쪽 기록 저장이 기록 유니크에 걸린다 = 기록은 이미 1건 있다.
     * 운영자가 "기록 없음"으로 오해하지 않게 로그에서 보관하지 못한 경우와 나눈다.
     */
    public static boolean isAlreadyRecorded(Throwable recordError) {
        return violates(recordError, PaymentRecovery.ORDER_UNIQUE);
    }

    // 제약 이름은 DB 오류 메시지로만 알 수 있어 원인 사슬의 메시지에서 찾는다
    private static boolean violates(Throwable error, String constraintName) {
        if (!(error instanceof DataIntegrityViolationException)) {
            return false;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 결제 내용 불일치는 다시 실행해도 같으므로 재처리할 수 없다.
     */
    public static boolean isRetryable(Throwable error) {
        return !(error instanceof PaymentMismatchException);
    }
}
