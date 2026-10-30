package com.bbcc.kidly.boundedcontext.payment.app.port;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;

/**
 * 아웃박스 메시지 하나를 Kafka로 보낸다(#550).
 * 브로커가 받았다고 답한 뒤에만 정상으로 돌아오고, 그렇지 않으면(시간 초과 포함) {@link PaymentEventSendException}을 던진다.
 * 예외가 나도 브로커에는 이미 기록됐을 수 있어, 다시 보내면 같은 메시지가 두 번 갈 수 있다. 받는 쪽은 eventId와 업무 상태로 거른다.
 */
public interface PaymentEventSender {

    void send(PaymentOutboxMessage message);
}
