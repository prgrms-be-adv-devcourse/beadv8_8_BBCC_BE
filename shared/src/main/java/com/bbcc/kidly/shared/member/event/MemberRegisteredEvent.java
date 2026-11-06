package com.bbcc.kidly.shared.member.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원이 가입했다. 결제 도메인이 지갑을 만든다. 이메일은 가입 때 받고 지금은 바꿀 수 없어 이 값을 복사해 둘 수 있다.
 * {@code resent}가 true면 관리자가 기존 회원을 다시 보낸 것이다(새 가입이 아니다). 받는 쪽은 복사본만 채우고
 * 가입 때만 할 일(환영 메일 등)은 하지 않는다. 이 필드가 없는 예전 메시지는 null이고 새 가입으로 본다.
 */
public record MemberRegisteredEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

    public record Data(Long memberId, String email, Boolean resent) {

        // 새 가입
        public Data(Long memberId, String email) {
            this(memberId, email, false);
        }
    }

    @Override
    public String aggregateId() {
        return String.valueOf(data.memberId());
    }
}
