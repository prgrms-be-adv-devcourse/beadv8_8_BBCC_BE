package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.app.DeadLetterReplay;

/**
 * 저장한 DLT 메시지를 원래 Kafka 수신 처리로 다시 실행한다(#601). 원래 토픽에 다시 넣지 않는다.
 * 구현은 in/event(원래 토픽의 Kafka 리스너를 그대로 부른다). 트랜잭션 없이 불러야 한다: 처리 · 실패 보관이 각자 트랜잭션에서 커밋한다.
 */
public interface DeadLetterReplayer {

    /**
     * 처리를 마쳤거나(이미 처리된 이벤트면 결과 1회분) 주문 자체 보관에 넘겼으면 정상 반환, 아니면 예외를 그대로 던진다.
     */
    void replay(DeadLetterReplay message);
}
