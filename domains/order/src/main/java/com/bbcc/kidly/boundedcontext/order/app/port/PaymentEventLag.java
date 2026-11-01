package com.bbcc.kidly.boundedcontext.order.app.port;


/**
 * 주문이 아직 처리하지 않은 결제 이벤트가 남아 있는지 확인한다(#561). 구현은 out/messaging(Kafka 구독 그룹 kidly.order의
 * kidly.payment.events 커밋 위치 vs 끝 위치). 결제 마감 만료가 처리 전 payment.completed가 남은 주문을 취소하지 않게 쓴다.
 * 예외를 던지지 않고, 확인하지 못하면(시간 초과 · 무응답) CHECK_FAILED를 돌려준다.
 */
public interface PaymentEventLag {

    Check check();

    /**
     * 결제 이벤트 처리 대기량 확인 결과(#561 · #642, 포트 반환값).
     *
     * @param status 결과 종류
     * @param lag 처리하지 않은 메시지 수. LAGGING일 때만 0보다 크다
     * @param reason 확인하지 못한 이유 코드(로그용, 메시지 본문 없음). CHECK_FAILED일 때만 있다
     */
    record Check(Status status, long lag, String reason) {

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

        public static Check caughtUp() {
            return new Check(Status.CAUGHT_UP, 0, null);
        }

        public static Check lagging(long lag) {
            return new Check(Status.LAGGING, lag, null);
        }

        public static Check notAssigned() {
            return new Check(Status.NOT_ASSIGNED, 0, null);
        }

        public static Check checkFailed(String reason) {
            return new Check(Status.CHECK_FAILED, 0, reason);
        }
    }
}
