package com.bbcc.kidly.boundedcontext.order.domain.expiry;

/**
 * 결제 이벤트 처리 대기량 확인 결과(#561).
 *
 * @param status 결과 종류
 * @param lag 처리하지 않은 메시지 수. LAGGING일 때만 0보다 크다
 * @param reason 확인하지 못한 이유 코드(로그용, 메시지 본문 없음). CHECK_FAILED일 때만 있다
 */
public record PaymentEventLagCheck(Status status, long lag, String reason) {

    public enum Status {
        /** 모두 처리했다 */
        CAUGHT_UP,
        /** 처리하지 않은 메시지가 있다 */
        LAGGING,
        /** 읽을 메시지가 있는데 그룹의 커밋 위치가 없다(파티션 배정 · 첫 처리 전) */
        NOT_ASSIGNED,
        /** 확인하지 못했다(Kafka 무응답 · 시간 초과 등) */
        CHECK_FAILED
    }

    public static PaymentEventLagCheck caughtUp() {
        return new PaymentEventLagCheck(Status.CAUGHT_UP, 0, null);
    }

    public static PaymentEventLagCheck lagging(long lag) {
        return new PaymentEventLagCheck(Status.LAGGING, lag, null);
    }

    public static PaymentEventLagCheck notAssigned() {
        return new PaymentEventLagCheck(Status.NOT_ASSIGNED, 0, null);
    }

    public static PaymentEventLagCheck checkFailed(String reason) {
        return new PaymentEventLagCheck(Status.CHECK_FAILED, 0, reason);
    }
}
