package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.out.JpaMemberRepository;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.dto.MemberRegistration;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.out.MemberAccounts;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.AsyncWait;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@RecordApplicationEvents
class MemberAccountsServiceIntegrationTest {

    private static final String KEY = "social-account:1";
    private static final String NICKNAME = "가입회원";
    private static final String EMAIL = "member@example.com";

    @Autowired
    private MemberAccounts memberAccounts;

    @Autowired
    private MemberRegistrar memberRegistrar;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JpaMemberRepository jpaMemberRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("가입 키로 가입하면 활성 회원이 되고 가입 이벤트를 발행한다")
    void register() {
        var state = memberAccounts.register(new MemberRegistration(KEY, NICKNAME, EMAIL));

        assertThat(state).isEqualTo(new MemberLoginState.Active(state.memberId(), NICKNAME, List.of(Role.BUYER)));
        assertThat(memberRepository.findById(state.memberId()))
            .hasValueSatisfying(member -> {
                assertThat(member.getRegistrationKey()).isEqualTo(KEY);
                assertThat(member.getEmail()).isEqualTo(EMAIL);
            });
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.data().memberId()).isEqualTo(state.memberId());
                assertThat(event.data().email()).isEqualTo(EMAIL);
            });
    }

    @Test
    @DisplayName("같은 키로 두 번 가입하면 회원 1명 · 가입 이벤트 1건이고 같은 회원을 돌려준다")
    void registerTwiceWithSameKey() {
        var first = memberAccounts.register(new MemberRegistration(KEY, NICKNAME, EMAIL));

        var second = memberAccounts.register(new MemberRegistration(KEY, "다른닉네임", "other@example.com"));

        assertThat(second).isEqualTo(first);
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("같은 키 · 같은 닉네임으로 동시에 가입하면 회원 1명 · 가입 이벤트 1건이고 모두 그 회원을 받는다")
    void concurrentRegisterWithSameKey() throws Exception {
        var threadCount = 10;
        var start = new CountDownLatch(1);
        Callable<Long> register = () -> {
            start.await();
            return memberAccounts.register(new MemberRegistration(KEY, NICKNAME, EMAIL)).memberId();
        };

        var memberIds = new ArrayList<Long>();
        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            var futures = new ArrayList<Future<Long>>();
            for (var i = 0; i < threadCount; i++) {
                futures.add(executor.submit(register));
            }
            start.countDown();
            for (var future : futures) {
                memberIds.add(future.get());
            }
        }

        assertThat(memberIds).hasSize(threadCount).containsOnly(memberIds.getFirst());
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().memberId()).isEqualTo(memberIds.getFirst()));
    }

    @Test
    @DisplayName("같은 키 · 같은 닉네임 가입이 앞 가입의 커밋을 기다리다 닉네임 유니크에 걸려도 그 회원을 돌려준다")
    void registerBlockedBehindSameKeyAndNickname() throws Exception {
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactionTemplate.execute(status -> {
                var member = memberRegistrar.register(new MemberRegisterInfo(KEY, new Nickname(NICKNAME), EMAIL));
                holding.countDown();
                await(release);
                return member.getId();
            }));
            await(holding);

            var loser = executor.submit(() -> memberAccounts.register(new MemberRegistration(KEY, NICKNAME, EMAIL)));
            AsyncWait.until(this::someoneWaitsForLock);
            release.countDown();

            assertThat(loser.get().memberId()).isEqualTo(winner.get());
        }
        assertThat(jpaMemberRepository.count()).isEqualTo(1);
        assertThat(events.stream(MemberRegisteredEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("다른 키로 같은 닉네임 힌트를 주면 닉네임에 숫자를 붙여 둘 다 가입한다")
    void differentKeysWithSameNicknameHint() {
        var first = memberAccounts.register(new MemberRegistration("social-account:1", NICKNAME, EMAIL));

        var second = memberAccounts.register(new MemberRegistration("social-account:2", NICKNAME, EMAIL));

        assertThat(second.memberId()).isNotEqualTo(first.memberId());
        assertThat(second).isInstanceOfSatisfying(MemberLoginState.Active.class,
            active -> assertThat(active.nickname()).matches(NICKNAME + "\\d{4}"));
        assertThat(events.stream(MemberRegisteredEvent.class)).hasSize(2);
    }

    @Test
    @DisplayName("숫자를 붙인 닉네임까지 모두 쓰이고 있으면 가입하지 않고 DataIntegrityViolationException을 던진다")
    void nicknameExhausted() {
        occupyAllNumberedNicknames(NICKNAME);

        assertThatThrownBy(() -> memberAccounts.register(new MemberRegistration(KEY, NICKNAME, EMAIL)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from member.member where registration_key = ?", Long.class, KEY)).isZero();
        assertThat(events.stream(MemberRegisteredEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("활성 회원의 로그인 상태는 판매자 · 관리자 역할을 담는다")
    void findLoginStateOfSellerAdmin() {
        var member = MemberFixture.withRole("kakao-1", MemberRole.ADMIN);
        member.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);
        var saved = memberRepository.save(member);

        assertThat(memberAccounts.findLoginState(saved.getId())).contains(new MemberLoginState.Active(
            saved.getId(), saved.getNickname(), List.of(Role.BUYER, Role.SELLER, Role.ADMIN)));
    }

    @Test
    @DisplayName("정지 · 탈퇴한 회원의 로그인 상태는 Suspended · Withdrawn이다")
    void findLoginStateOfInactive() {
        var suspended = memberRepository.save(MemberFixture.withStatus("kakao-1", MemberStatus.SUSPENDED));
        var withdrawn = memberRepository.save(MemberFixture.withStatus("kakao-2", MemberStatus.WITHDRAWN));

        assertThat(memberAccounts.findLoginState(suspended.getId()))
            .contains(new MemberLoginState.Suspended(suspended.getId()));
        assertThat(memberAccounts.findLoginState(withdrawn.getId()))
            .contains(new MemberLoginState.Withdrawn(withdrawn.getId()));
    }

    @Test
    @DisplayName("없는 회원의 로그인 상태는 비어 있다")
    void findLoginStateOfUnknown() {
        assertThat(memberAccounts.findLoginState(Long.MAX_VALUE)).isEmpty();
    }

    private void occupyAllNumberedNicknames(String base) {
        jdbcTemplate.update("""
            insert into member.member (nickname, email, role, status, created_at, updated_at)
            select ? || n, null, 'USER', 'ACTIVE', now(), now()
            from (select '' as n union all select generate_series(?, ?)::text) numbers
            """, base, Nickname.MIN_NUMBER, Nickname.MAX_NUMBER);
    }

    private boolean someoneWaitsForLock() {
        return jdbcTemplate.queryForObject(
            "select count(*) from pg_stat_activity where wait_event_type = 'Lock'", Long.class) > 0;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
