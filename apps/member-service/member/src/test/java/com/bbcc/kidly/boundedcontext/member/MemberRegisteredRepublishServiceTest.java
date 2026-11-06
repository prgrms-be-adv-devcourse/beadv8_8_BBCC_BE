package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.member.app.MemberRegisteredRepublishService;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberRegisteredRepublishServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberRegisteredRepublishService memberRegisteredRepublishService;

    @Test
    @DisplayName("이메일이 있는 회원마다 가입 이벤트를 resent=true로 한 번씩 새로 발행하고, 보낸 회원 수를 돌려준다")
    void republishAll() {
        var first = withId(MemberFixture.kakaoMember("1"), 1L);
        var second = withId(MemberFixture.kakaoMember("2"), 2L);
        given(memberRepository.findByEmailIsNotNull()).willReturn(List.of(first, second));
        var before = LocalDateTime.now();

        int publishedCount = memberRegisteredRepublishService.republishAll();

        assertThat(publishedCount).isEqualTo(2);
        var captor = ArgumentCaptor.forClass(MemberRegisteredEvent.class);
        then(eventPublisher).should(times(2)).publishEvent(captor.capture());
        var events = captor.getAllValues();
        assertThat(events)
            .extracting(event -> event.data().memberId(), event -> event.data().email(), event -> event.data().resent())
            .containsExactly(tuple(1L, "1@example.com", true), tuple(2L, "2@example.com", true));
        assertThat(events.get(0).eventId()).isNotEqualTo(events.get(1).eventId());
        assertThat(events).allSatisfy(event -> assertThat(event.occurredAt()).isAfterOrEqualTo(before));
    }

    @Test
    @DisplayName("이메일이 있는 회원이 없으면 아무것도 발행하지 않고 0을 돌려준다")
    void republishNone() {
        given(memberRepository.findByEmailIsNotNull()).willReturn(List.of());

        assertThat(memberRegisteredRepublishService.republishAll()).isZero();
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    private static Member withId(Member member, Long id) {
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
