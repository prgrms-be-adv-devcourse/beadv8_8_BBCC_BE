package com.bbcc.kidly.boundedcontext.activity.app.port;

import java.time.Duration;
import java.util.UUID;

/**
 * 커뮤니티 이벤트를 토픽(kidly.community.events)으로 보낸다(한 건씩, 브로커가 받을 때까지 기다린다). 구현은 out/messaging.
 * 도메인 이벤트를 커밋 뒤 발행하는 공용 EventPublisher(common)와 다른 것이다(아웃박스 릴레이 전용).
 */
public interface CommunityEventSender {

    /**
     * @param timeout 브로커 응답을 기다리는 최대 시간
     * @throws SendFailedException 보내지 못했을 때(시간 초과 · 브로커 거절 등). 메시지에 키 · 본문을 넣지 않는다
     */
    void send(String key, String eventType, UUID eventId, String payload, Duration timeout);

    /**
     * 보내지 못했다.
     */
    class SendFailedException extends RuntimeException {

        private final String failureType;

        public SendFailedException(String failureType) {
            super("커뮤니티 이벤트를 보내지 못했습니다: " + failureType);
            this.failureType = failureType;
        }

        public String failureType() {
            return failureType;
        }
    }
}
