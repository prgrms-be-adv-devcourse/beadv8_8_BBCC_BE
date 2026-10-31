package com.bbcc.kidly.boundedcontext.order.app.deadletter;

/**
 * 받은 메시지를 읽을 수 없다(#601): 종류를 고를 eventType 헤더가 없다. 다시 해도 같으므로 재시도 없이 DLT로 가고(IllegalArgumentException),
 * DLT에서는 읽기 실패(READ_FAILED)로 분류한다. 메시지에는 본문 대신 위치만 넣는다.
 */
public class UnreadableOrderMessageException extends IllegalArgumentException {

    public UnreadableOrderMessageException(String message) {
        super(message);
    }
}
