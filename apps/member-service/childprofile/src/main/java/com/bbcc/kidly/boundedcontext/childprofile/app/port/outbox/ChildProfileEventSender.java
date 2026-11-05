package com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;

/**
 * 아웃박스 메시지를 브로커에 보낸다.
 * 브로커가 받았다고 응답한 뒤에만 정상으로 돌아오고, 그렇지 않으면 {@link ChildProfileEventSendException}을 던진다.
 * 예외가 나도 브로커에는 이미 기록됐을 수 있으므로, 다시 보내면 같은 메시지가 두 번 갈 수 있다.
 * 받는 쪽(주문 · 추천 복사본)은 occurredAt 비교로 중복을 거른다.
 */
public interface ChildProfileEventSender {

    void send(ChildProfileOutboxMessage message);
}
