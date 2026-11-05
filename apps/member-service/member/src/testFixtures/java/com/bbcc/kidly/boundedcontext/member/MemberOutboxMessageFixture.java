package com.bbcc.kidly.boundedcontext.member;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import java.util.UUID;

public final class MemberOutboxMessageFixture {

    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // 로그에 남지 않아야 하는 본문이다
    public static final String PAYLOAD =
        "{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"memberId\":1}}";

    private MemberOutboxMessageFixture() {
    }

    /**
     * 회원 1의 탈퇴를 kidly.member.events로 보낼 메시지다. 키는 회원 ID다.
     */
    public static MemberOutboxMessage.CreateInfo withdrawn() {
        return withdrawn(EVENT_ID);
    }

    /**
     * 이벤트 ID만 바꾼 회원 1의 메시지다. 여러 메시지를 저장할 때 event_id UNIQUE에 걸리지 않게 쓴다.
     */
    public static MemberOutboxMessage.CreateInfo withdrawn(UUID eventId) {
        return new MemberOutboxMessage.CreateInfo(eventId, "member.withdrawn", "kidly.member.events", "1", PAYLOAD);
    }
}
