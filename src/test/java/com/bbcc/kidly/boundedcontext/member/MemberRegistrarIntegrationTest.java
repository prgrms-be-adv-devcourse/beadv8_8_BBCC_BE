package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.bbcc.kidly.boundedcontext.member.out.JpaMemberRepository;
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
    private JpaMemberRepository jpaMemberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("가입하면 회원을 저장하고 가입 이벤트를 발행한다")
    void register() {
        Member member = memberRegistrar.register(
            new MemberRegisterInfo(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"), "테스트회원", MemberFixture.EMAIL));

        assertThat(memberRepository.findBySocialAccount(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1")))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(member.getId()));
        assertThat(events.stream(MemberRegisteredEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.data().memberId()).isEqualTo(member.getId());
            assertThat(event.data().email()).isEqualTo(MemberFixture.EMAIL);
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정으로 가입하면 실패하고 가입 이벤트를 발행하지 않는다")
    void duplicateSocialAccount() {
        memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThatThrownBy(() -> memberRegistrar.register(
            new MemberRegisterInfo(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"), "다른회원", MemberFixture.EMAIL)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }
}
