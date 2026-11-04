package com.bbcc.kidly.boundedcontext.member.app.port.outbox;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;

/**
 * 아웃박스 메시지를 브로커에 보낸다.
 * 브로커가 받았다고 응답한 뒤에만 정상으로 돌아오고, 그렇지 않으면 {@link MemberOutboxSendException}을 던진다.
 * 예외가 나도 브로커에는 이미 기록됐을 수 있으므로, 다시 보내면 같은 메시지가 두 번 갈 수 있다.
 * 받는 쪽(자녀 프로필)은 이미 지운 자녀를 다시 찾지 않아 두 번 받아도 결과가 같다.
 */
public interface MemberOutboxSender {

    void send(MemberOutboxMessage message);
}
