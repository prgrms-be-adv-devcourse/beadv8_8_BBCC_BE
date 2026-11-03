package com.bbcc.kidly.boundedcontext.order.app.port;


/**
 * 아웃박스 메시지 하나를 Kafka로 보낸다(#540). 구현은 out/kafka.
 * 브로커가 받았다고 답할 때까지 기다리고, 보내지 못하면(시간 초과 포함) 예외를 던진다. 예외 메시지에 본문을 넣지 않는다.
 */
public interface OrderEventSender {

    void send(OrderOutboxRepository.OutboxMessage message);

    /**
     * 메시지 자체가 거절돼 전송되지 않았고, 원인 수정 없이 재시도해도 실패하는 경우만 true.
     * 타임아웃 · 연결 · 인증 · 토픽 설정 오류 등 전송 여부가 불명확하거나 환경 문제인 경우는 false.
     */
    default boolean isPermanentFailure(RuntimeException error) {
        return false;
    }
}
