package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberQueryRepository;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class MemberRegistrarIntegrationTest {

    @Autowired
    private MemberRegistrar memberRegistrar;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberQueryRepository memberQueryRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("가입하면 회원을 저장하고 가입 이벤트를 발행한다")
    void register() {
        Member member = memberRegistrar.register(new MemberLoginRequest(OAuthProvider.KAKAO, "kakao-1", "테스트회원"));

        assertThat(memberQueryRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-1"))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(member.getId()));
        assertThat(events.stream(MemberRegisteredEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.data().memberId()).isEqualTo(member.getId());
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정으로 가입하면 실패하고 가입 이벤트를 발행하지 않는다")
    void duplicateSocialAccount() {
        memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThatThrownBy(() -> memberRegistrar.register(
            new MemberLoginRequest(OAuthProvider.KAKAO, "kakao-1", "다른회원")))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }
}
