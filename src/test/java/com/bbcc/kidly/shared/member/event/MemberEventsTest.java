package com.bbcc.kidly.shared.member.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("회원가입 이벤트는 member.registered이고 대상은 회원이다")
    void memberRegistered() {
        assertContract(new MemberRegisteredEvent(EVENT_ID, NOW, new MemberRegisteredEvent.Data(1L)),
            "member.registered", "1");
    }

    @Test
    @DisplayName("회원탈퇴 이벤트는 member.withdrawn이고 대상은 회원이다")
    void memberWithdrawn() {
        assertContract(new MemberWithdrawnEvent(EVENT_ID, NOW, new MemberWithdrawnEvent.Data(1L)),
            "member.withdrawn", "1");
    }

    private static void assertContract(DomainEvent event, String eventType, String aggregateId) {
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.producer()).isEqualTo("member");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.correlationId()).isEqualTo(aggregateId);
    }
}
