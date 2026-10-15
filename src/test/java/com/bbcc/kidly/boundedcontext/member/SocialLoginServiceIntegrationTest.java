package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.member.app.dto.LoggedInMember;
import com.bbcc.kidly.boundedcontext.member.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.out.JpaMemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.FakeOAuthClient;
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
    private JpaMemberRepository jpaMemberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("처음 로그인하면 가입되고 가입 이벤트가 발행된다")
    void firstLoginRegisters() {
        var member = login("kakao-1");

        assertThat(memberRepository.findById(member.memberId())).isPresent();
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.data().memberId()).isEqualTo(member.memberId());
                assertThat(event.data().email()).isEqualTo(FakeOAuthClient.EMAIL);
            });
    }

    @Test
    @DisplayName("이미 가입한 회원은 가입 없이 로그인한다")
    void existingMemberLogsIn() {
        Member saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        var member = login("kakao-1");

        assertThat(member.memberId()).isEqualTo(saved.getId());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
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

    @Test
    @DisplayName("인가 코드로 로그인하면 제공자의 회원 번호 · 닉네임 · 이메일로 가입한다")
    void registersWithProviderProfile() {
        var member = login("kakao-1");

        assertThat(memberRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-1"))
            .hasValueSatisfying(found -> {
                assertThat(found.getId()).isEqualTo(member.memberId());
                assertThat(found.getNickname()).isEqualTo(FakeOAuthClient.NICKNAME);
                assertThat(found.getEmail()).isEqualTo(FakeOAuthClient.EMAIL);
            });
    }

    @Test
    @DisplayName("제공자 이메일 없이 처음 로그인하면 가입되지 않는다")
    void firstLoginWithoutEmail() {
        assertThatThrownBy(() -> login(FakeOAuthClient.NO_EMAIL_CODE))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.EMAIL_REQUIRED));
        assertThat(jpaMemberRepository.count()).isZero();
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("이메일을 받기 전에 가입한 회원은 제공자 이메일 없이도 로그인한다")
    void existingMemberWithoutEmailLogsIn() {
        Member saved = memberRepository.save(MemberFixture.withoutEmail(FakeOAuthClient.NO_EMAIL_CODE));

        var member = login(FakeOAuthClient.NO_EMAIL_CODE);

        assertThat(member.memberId()).isEqualTo(saved.getId());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("제공자 닉네임이 없으면 회원 + 숫자 4자리로 가입한다")
    void defaultNickname() {
        var member = login(FakeOAuthClient.NO_NICKNAME_CODE);

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getNickname()).matches("회원\\d{4}"));
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임이면 뒤에 숫자 4자리를 붙여 가입한다")
    void duplicateNicknameGetsNumber() {
        login("kakao-1");

        var member = login("kakao-2");

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getNickname()).matches(FakeOAuthClient.NICKNAME + "\\d{4}"));
    }

    @Test
    @DisplayName("제공자 닉네임이 20자를 넘으면 20자로 자른다")
    void longNicknameIsCut() {
        var member = login(FakeOAuthClient.LONG_NICKNAME_CODE_PREFIX + 1);

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getNickname()).isEqualTo("가".repeat(20)));
    }

    @Test
    @DisplayName("잘린 닉네임도 겹치면 앞 16자에 숫자 4자리를 붙인다")
    void longDuplicateNicknameGetsNumber() {
        login(FakeOAuthClient.LONG_NICKNAME_CODE_PREFIX + 1);

        var member = login(FakeOAuthClient.LONG_NICKNAME_CODE_PREFIX + 2);

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getNickname()).matches("가{16}\\d{4}"));
    }

    @Test
    @DisplayName("제공자 닉네임이 2자 미만이면 회원 + 숫자 4자리로 가입한다")
    void shortNickname() {
        var member = login(FakeOAuthClient.SHORT_NICKNAME_CODE);

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getNickname()).matches("회원\\d{4}"));
    }

    @Test
    @DisplayName("지원하지 않는 제공자면 로그인할 수 없다")
    void unsupportedProvider() {
        assertThatThrownBy(() -> socialLoginService.login("naver", new SocialLoginRequest("naver-1")))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }

    // FakeOAuthClient는 인가 코드를 그대로 providerUserId로 돌려준다
    private LoggedInMember login(String providerUserId) {
        return socialLoginService.login("kakao", new SocialLoginRequest(providerUserId));
    }
}
