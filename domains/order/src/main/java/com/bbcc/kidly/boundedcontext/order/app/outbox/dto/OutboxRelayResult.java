package com.bbcc.kidly.boundedcontext.order.app.outbox.dto;

/**
 * 릴레이 한 번 실행의 결과.
 *
 * @param targets 읽은 안 보낸 메시지 수
 * @param sent 이번에 보낸 수
 */
public record OutboxRelayResult(Outcome outcome, int targets, int sent) {

    public enum Outcome {
        /**
         * 다른 릴레이가 잠금을 쥐고 있어 아무것도 하지 않았다.
         */
        LOCKED,
        /**
         * 읽은 메시지를 모두 보냈다(0건 포함).
         */
        COMPLETED,
        /**
         * 보내다 실패해 그 자리에서 멈췄다. 다음 실행이 그 메시지부터 다시 보낸다.
         */
        STOPPED
    }
}
