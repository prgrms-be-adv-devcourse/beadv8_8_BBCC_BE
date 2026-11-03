package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.app.SocialLoginService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.out.JpaMemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.dto.MemberRegistration;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.out.MemberAccounts;
import com.bbcc.kidly.support.FakeOAuthClient;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class SocialLoginServiceIntegrationTest {

    @Autowired
    private SocialLoginService socialLoginService;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private MemberAccounts memberAccounts;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JpaMemberRepository jpaMemberRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 소셜 계정으로 동시에 처음 로그인하면 한 명만 가입되고 모두 그 회원으로 로그인한다")
    void concurrentFirstLoginRegistersOnce() throws Exception {
        var threadCount = 10;
        var start = new CountDownLatch(1);
        Callable<Long> login = () -> {
            start.await();
            return login("kakao-1").memberId();
        };

        List<Long> memberIds;
        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            var futures = new ArrayList<Future<Long>>();
            for (var i = 0; i < threadCount; i++) {
                futures.add(executor.submit(login));
            }
            start.countDown();
            memberIds = new ArrayList<>();
            for (var future : futures) {
                memberIds.add(future.get());
            }
        }

        assertThat(memberIds).hasSize(threadCount).containsOnly(memberIds.getFirst());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth.social_account", Long.class))
            .isEqualTo(1L);
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().memberId()).isEqualTo(memberIds.getFirst()));
    }

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
        var saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));
        socialAccountRepository.save(SocialAccountFixture.linked(saved.getId(), "kakao-1"));

        var member = login("kakao-1");

        assertThat(member.memberId()).isEqualTo(saved.getId());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("정지한 회원은 Suspended 상태로 돌려준다")
    void suspendedMemberReturnsSuspended() {
        var saved = memberRepository.save(MemberFixture.withStatus(MemberStatus.SUSPENDED));
        socialAccountRepository.save(SocialAccountFixture.linked(saved.getId(), "kakao-1"));

        assertThat(login("kakao-1")).isEqualTo(new MemberLoginState.Suspended(saved.getId()));
    }

    @Test
    @DisplayName("선점 행만 남아 있으면(앞 시도가 가입 전에 죽음) 그 행을 이어받아 가입하고 연결한다")
    void claimedAccountIsTakenOver() {
        var claimed = socialAccountRepository.save(SocialAccountFixture.claimed("kakao-1"));

        var member = login("kakao-1");

        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> assertThat(found.getRegistrationKey()).isEqualTo(claimed.registrationKey()));
        assertThat(socialAccountRepository.findByIdentity(SocialAccountFixture.kakao("kakao-1")))
            .hasValueSatisfying(account -> {
                assertThat(account.getId()).isEqualTo(claimed.getId());
                assertThat(account.linkedMemberId()).contains(member.memberId());
            });
        assertThat(events.stream(MemberRegisteredEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("가입은 됐지만 연결 전에 죽었으면 다음 로그인이 같은 회원으로 연결하고 가입 이벤트를 다시 내지 않는다")
    void registeredButUnlinkedAccountIsLinked() {
        var claimed = socialAccountRepository.save(SocialAccountFixture.claimed("kakao-1"));
        var registered = memberAccounts.register(
            new MemberRegistration(claimed.registrationKey(), FakeOAuthClient.NICKNAME, FakeOAuthClient.EMAIL));

        var member = login("kakao-1");

        assertThat(member.memberId()).isEqualTo(registered.memberId());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.findByMemberId(registered.memberId())).hasSize(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴 리스너가 아직 연결을 지우지 못했어도 같은 소셜 계정으로 로그인하면 새 회원이 된다")
    void withdrawnLinkRegistersNewMember() {
        var withdrawn = memberRepository.save(MemberFixture.withStatus(MemberStatus.WITHDRAWN));
        socialAccountRepository.save(SocialAccountFixture.linked(withdrawn.getId(), "kakao-1"));

        var member = login("kakao-1");

        assertThat(member).isInstanceOf(MemberLoginState.Active.class);
        assertThat(member.memberId()).isNotEqualTo(withdrawn.getId());
        assertThat(socialAccountRepository.findByMemberId(withdrawn.getId())).isEmpty();
        assertThat(socialAccountRepository.findByMemberId(member.memberId())).hasSize(1);
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().memberId()).isEqualTo(member.memberId()));
    }

    @Test
    @DisplayName("인가 코드로 로그인하면 제공자의 회원 번호 · 닉네임 · 이메일로 가입한다")
    void registersWithProviderProfile() {
        var member = login("kakao-1");

        assertThat(socialAccountRepository.findByIdentity(SocialAccountFixture.kakao("kakao-1")))
            .hasValueSatisfying(account -> assertThat(account.linkedMemberId()).contains(member.memberId()));
        assertThat(memberRepository.findById(member.memberId()))
            .hasValueSatisfying(found -> {
                assertThat(found.getNickname()).isEqualTo(FakeOAuthClient.NICKNAME);
                assertThat(found.getEmail()).isEqualTo(FakeOAuthClient.EMAIL);
            });
    }

    @Test
    @DisplayName("제공자 이메일 없이 처음 로그인하면 가입되지 않고 선점 행도 남지 않는다")
    void firstLoginWithoutEmail() {
        assertThatThrownBy(() -> login(FakeOAuthClient.NO_EMAIL_CODE))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.EMAIL_REQUIRED));
        assertThat(jpaMemberRepository.count()).isZero();
        assertThat(socialAccountRepository.findByIdentity(SocialAccountFixture.kakao(FakeOAuthClient.NO_EMAIL_CODE)))
            .isEmpty();
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("이메일을 받기 전에 가입한 회원은 제공자 이메일 없이도 로그인한다")
    void existingMemberWithoutEmailLogsIn() {
        var saved = memberRepository.save(MemberFixture.withoutEmail(FakeOAuthClient.NO_EMAIL_CODE));
        socialAccountRepository.save(SocialAccountFixture.linked(saved.getId(), FakeOAuthClient.NO_EMAIL_CODE));

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
        assertThatThrownBy(() -> socialLoginService.signIn("naver", new SocialLoginRequest("naver-1")))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }

    @Test
    @DisplayName("연결된 제공자 이름을 돌려주고, 연결이 없거나 가입 진행 중이면 빈 목록이다")
    void findProviders() {
        var linked = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));
        socialAccountRepository.save(SocialAccountFixture.linked(linked.getId(), "kakao-1"));
        var unlinked = memberRepository.save(MemberFixture.kakaoMember("kakao-2"));
        socialAccountRepository.save(SocialAccountFixture.claimed("kakao-2"));

        assertThat(socialLoginService.findProviders(linked.getId())).containsExactly("KAKAO");
        assertThat(socialLoginService.findProviders(unlinked.getId())).isEmpty();
    }

    // FakeOAuthClient는 인가 코드를 그대로 providerUserId로 돌려준다
    private MemberLoginState login(String providerUserId) {
        return socialLoginService.signIn("kakao", new SocialLoginRequest(providerUserId));
    }
}
