package com.bbcc.kidly.boundedcontext.product.app.port.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;

/**
 * 아웃박스 메시지를 브로커에 보낸다.
 */
public interface ProductEventSender {

    void send(ProductOutboxMessage message);
}
