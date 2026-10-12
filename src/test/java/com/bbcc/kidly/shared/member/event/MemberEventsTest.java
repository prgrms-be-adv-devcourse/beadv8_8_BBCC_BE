package com.bbcc.kidly.shared.member.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import com.bbcc.kidly.shared.member.domain.SellerStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    @DisplayName("자녀 변경 이벤트는 child.changed이고 대상은 자녀다")
    void childChanged() {
        ChildChangedEvent event = new ChildChangedEvent(EVENT_ID, NOW, new ChildChangedEvent.Data(10L, 1L,
            new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2019, 1, 1),
            BodyType.NORMAL, PreferredFit.LOOSE, false));

        assertContract(event, "child.changed", "10");
    }

    @Test
    @DisplayName("판매자 변경 이벤트는 seller.changed이고 대상은 판매자다")
    void sellerChanged() {
        assertContract(new SellerChangedEvent(EVENT_ID, NOW,
                new SellerChangedEvent.Data(2L, "테스트상점", SellerStatus.ACTIVE)),
            "seller.changed", "2");
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
