package com.bbcc.kidly.boundedcontext.order.app.deadletter;

/**
 * 재처리할 DLT 메시지의 원래 토픽을 주문이 받지 않는다(#601): 원래 위치 헤더가 없어 DLT 자신의 토픽이 저장된 경우 등.
 * 처리할 리스너가 없으므로 재처리 실패로 남는다.
 */
public class UnknownDeadLetterSourceException extends IllegalStateException {

    public UnknownDeadLetterSourceException(String sourceTopic) {
        super("주문이 받지 않는 원래 토픽: " + sourceTopic);
    }
}
