package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@RecordApplicationEvents
class ChildProfileMemberEventListenerIntegrationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("회원이 탈퇴하면 그 회원의 자녀를 모두 삭제하고 자녀마다 삭제 이벤트를 발행한다")
    void deleteChildrenOnWithdraw() {
        var first = childProfileService.register(MEMBER_ID, REQUEST).id();
        var second = childProfileService.register(MEMBER_ID, REQUEST).id();
        var other = childProfileService.register(OTHER_MEMBER_ID, REQUEST).id();
        events.clear();

        publishWithdrawn(MEMBER_ID);

        assertThat(childProfileRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
        assertThat(childProfileRepository.findAllByMemberId(OTHER_MEMBER_ID))
            .extracting(child -> child.getId())
            .containsExactly(other);
        assertThat(events.stream(ChildChangedEvent.class))
            .allSatisfy(event -> {
                assertThat(event.data().memberId()).isEqualTo(MEMBER_ID);
                assertThat(event.data().deleted()).isTrue();
            })
            .extracting(event -> event.data().childId())
            .containsExactlyInAnyOrder(first, second);
    }

    // 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행된다
    private void publishWithdrawn(Long memberId) {
        var event = new MemberWithdrawnEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberWithdrawnEvent.Data(memberId));
        transactionTemplate.executeWithoutResult(transaction -> eventPublisher.publish(event));
    }
}
