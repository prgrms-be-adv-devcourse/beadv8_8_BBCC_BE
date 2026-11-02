package com.bbcc.kidly.boundedcontext.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.auth.app.AuthEventRelay;
import com.bbcc.kidly.boundedcontext.auth.app.port.AuthEventSender;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthEventRelayTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    @Mock
    private AuthEventSender authEventSender;

    @InjectMocks
    private AuthEventRelay authEventRelay;

    @Test
    @DisplayName("인증이 발행한 토큰 무효화는 커밋 뒤 바로 Kafka로 넘긴다")
    void relayTokensRevoked() {
        var event = new AuthTokensRevokedEvent(UUID.randomUUID(), NOW,
            new AuthTokensRevokedEvent.Data(PrincipalType.MEMBER, 1L, NOW));

        authEventRelay.relay(event);

        then(authEventSender).should().send(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 넘기지 않는다")
    void skipOtherProducer() {
        var event = new MemberChangedEvent(UUID.randomUUID(), NOW, new MemberChangedEvent.Data(1L, "새닉네임"));

        authEventRelay.relay(event);

        then(authEventSender).should(never()).send(any());
    }
}
