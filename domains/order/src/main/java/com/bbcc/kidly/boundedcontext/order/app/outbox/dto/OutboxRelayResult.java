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
         * 이번에 읽은 전송 가능한 메시지를 모두 보냈다(0건 포함).
         */
        COMPLETED,
        /**
         * 보내다 실패해 그 자리에서 멈췄다. 다음 실행이 그 메시지부터 다시 보낸다.
         */
        STOPPED,
        /**
         * 메시지 영구 실패를 격리하고 같은 키만 보류했다. 다른 키는 계속 처리했다.
         */
        QUARANTINED
    }
}
