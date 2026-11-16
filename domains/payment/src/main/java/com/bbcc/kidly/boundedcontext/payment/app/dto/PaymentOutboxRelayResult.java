package com.bbcc.kidly.boundedcontext.payment.app.dto;

/**
 * 릴레이 한 번 실행의 결과.
 *
 * @param targets 읽은 보낼 메시지 수
 * @param sent 이번에 보낸 수
 * @param parked 이번에 격리한 수
 */
public record PaymentOutboxRelayResult(Outcome outcome, int targets, int sent, int parked) {

    public enum Outcome {
        /**
         * 다른 릴레이가 잠금을 쥐고 있어 아무것도 하지 않았다.
         */
        LOCKED,
        /**
         * 읽은 메시지를 끝까지 처리했다(0건 포함). 시도 상한에 닿은 메시지와 그 키의 뒤 메시지는 남았을 수 있다.
         */
        COMPLETED,
        /**
         * 보내다 실패해 그 자리에서 멈췄다. 다음 실행이 그 메시지부터 다시 보낸다.
         */
        STOPPED
    }
}
