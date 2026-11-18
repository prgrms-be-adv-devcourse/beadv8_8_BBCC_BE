package com.bbcc.kidly.member.support.deadletter;

/**
 * 보관한 메시지를 원래 처리기로 다시 처리한다. 원래 토픽에 다시 넣지 않는다.
 */
public interface DeadLetterReplay {

    void replay(DeadLetterMessage message);
}
