package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.MemberFixture.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.member.app.MemberEventRelay;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberEventSender;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberEventRelayTest {

    @Mock
    private MemberEventSender memberEventSender;

    @InjectMocks
    private MemberEventRelay memberEventRelay;

    @Test
    @DisplayName("회원이 발행한 이벤트는 Kafka로 넘긴다")
    void relayMemberEvent() {
        var event = new MemberChangedEvent(UUID.randomUUID(), NOW, new MemberChangedEvent.Data(1L, "새닉네임"));

        memberEventRelay.relay(event);

        then(memberEventSender).should().send(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 넘기지 않는다")
    void skipOtherProducer() {
        var event = new SellerChangedEvent(UUID.randomUUID(), NOW,
            new SellerChangedEvent.Data(1L, "소소한옷장", SellerStatus.ACTIVE));

        memberEventRelay.relay(event);

        then(memberEventSender).should(never()).send(any());
    }

    @Test
    @DisplayName("탈퇴는 커밋 뒤 바로 넘기지 않는다(아웃박스 릴레이가 보낸다)")
    void skipWithdrawn() {
        var event = new MemberWithdrawnEvent(UUID.randomUUID(), NOW, new MemberWithdrawnEvent.Data(1L));

        memberEventRelay.relay(event);

        then(memberEventSender).should(never()).send(any());
    }
}
