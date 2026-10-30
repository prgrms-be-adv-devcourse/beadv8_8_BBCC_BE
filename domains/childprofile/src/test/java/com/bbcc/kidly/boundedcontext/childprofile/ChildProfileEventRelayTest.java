package com.bbcc.kidly.boundedcontext.childprofile;

import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileEventRelay;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.ChildProfileEventSender;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildProfileEventRelayTest {

    private static final Long CHILD_ID = 3L;
    private static final Long MEMBER_ID = 1L;

    @Mock
    private ChildProfileEventSender childProfileEventSender;

    @InjectMocks
    private ChildProfileEventRelay childProfileEventRelay;

    @Test
    @DisplayName("자녀 프로필이 발행한 이벤트는 Kafka로 넘긴다")
    void relayChildProfileEvent() {
        var event = new ChildChangedEvent(UUID.randomUUID(), NOW, new ChildChangedEvent.Data(
            CHILD_ID, MEMBER_ID, ChildProfileFixture.HEIGHT, ChildProfileFixture.WEIGHT, Gender.MALE,
            ChildProfileFixture.BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false));

        childProfileEventRelay.relay(event);

        then(childProfileEventSender).should().send(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 넘기지 않는다")
    void skipOtherProducer() {
        var event = new MemberWithdrawnEvent(UUID.randomUUID(), NOW, new MemberWithdrawnEvent.Data(MEMBER_ID));

        childProfileEventRelay.relay(event);

        then(childProfileEventSender).should(never()).send(any());
    }
}
