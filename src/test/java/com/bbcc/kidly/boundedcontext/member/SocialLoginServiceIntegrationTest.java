package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class SocialLoginServiceIntegrationTest {

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("처음 로그인하면 가입되고 가입 이벤트가 발행된다")
    void firstLoginRegisters() {
        var member = login("kakao-1");

        assertThat(memberRepository.findById(member.getId())).isPresent();
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().memberId()).isEqualTo(member.getId()));
    }

    @Test
    @DisplayName("이미 가입한 회원은 가입 없이 로그인한다")
    void existingMemberLogsIn() {
        Member saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        var member = login("kakao-1");

        assertThat(member.getId()).isEqualTo(saved.getId());
        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("정지 · 탈퇴한 회원은 로그인할 수 없다")
    void inactiveMemberCannotLogin(MemberStatus status) {
        memberRepository.save(MemberFixture.withStatus(status));

        assertThatThrownBy(() -> login("kakao-1"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.LOGIN_NOT_ALLOWED));
    }

    private Member login(String providerUserId) {
        return socialLoginService.login(new MemberLoginRequest(OAuthProvider.KAKAO, providerUserId, "테스트회원"));
    }
}
