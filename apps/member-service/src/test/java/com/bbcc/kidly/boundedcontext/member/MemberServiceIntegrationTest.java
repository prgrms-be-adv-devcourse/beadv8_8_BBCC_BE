package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("탈퇴하면 회원 정보를 지우고 탈퇴 이벤트를 발행한다. 배송지 · 소셜 계정 연결은 이 이벤트를 Kafka로 받아 지운다")
    void withdraw() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        memberService.withdraw(memberId);

        assertThat(memberRepository.findById(memberId)).hasValueSatisfying(member -> {
            assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
            assertThat(member.getWithdrawnAt()).isNotNull();
            assertThat(member.getNickname()).isNull();
            assertThat(member.getEmail()).isNull();
        });
        assertThat(events.stream(MemberWithdrawnEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.data().memberId()).isEqualTo(memberId);
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("탈퇴하면 이미 발급된 Access Token을 무효화한다(auth.tokens-revoked 발행)")
    void withdrawRevokesAccessToken() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        memberService.withdraw(memberId);

        assertThat(events.stream(AuthTokensRevokedEvent.class))
            .singleElement().satisfies(event -> assertThat(event.data().principalId()).isEqualTo(memberId));
    }
}
