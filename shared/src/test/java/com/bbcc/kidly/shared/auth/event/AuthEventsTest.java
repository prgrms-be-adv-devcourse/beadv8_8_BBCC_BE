package com.bbcc.kidly.shared.auth.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.auth.PrincipalType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthEventsTest {

    private static final UUID EVENT_ID = UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("토큰 무효화 이벤트는 auth.tokens-revoked이고 인증이 발행하며 대상은 주체 ID다")
    void authTokensRevoked() {
        var event = new AuthTokensRevokedEvent(EVENT_ID, NOW,
            new AuthTokensRevokedEvent.Data(PrincipalType.MEMBER, 1L, NOW));

        assertThat(event.eventType()).isEqualTo("auth.tokens-revoked");
        assertThat(event.producer()).isEqualTo("auth");
        assertThat(event.aggregateId()).isEqualTo("1");
        assertThat(event.correlationId()).isEqualTo("1");
    }
}
