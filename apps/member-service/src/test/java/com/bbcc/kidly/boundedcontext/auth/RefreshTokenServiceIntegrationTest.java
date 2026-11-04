package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.auth.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.auth.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.member.MemberFixture;
import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.PrincipalType;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class RefreshTokenServiceIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("발급하면 원문은 돌려주고 DB에는 14일짜리 해시만 저장한다")
    void issue() {
        var member = saveMember();

        var token = refreshTokenService.issue(member);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(refreshTokenRepository.findByTokenHash(RefreshToken.hashOf(token)))
            .hasValueSatisfying(saved -> {
                assertThat(saved.principal()).isEqualTo(member);
                assertThat(saved.getExpiresAt())
                    .isCloseTo(LocalDateTime.now().plusDays(14), within(1, ChronoUnit.MINUTES));
            });
    }

    @Test
    @DisplayName("다시 로그인하면 토큰이 바뀌어 이전 토큰으로는 재발급할 수 없다")
    void issueAgainReplaces() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);

        refreshTokenService.issue(member);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertInvalid(() -> reissueMember(oldToken));
    }

    @Test
    @DisplayName("재발급하면 새 토큰을 주고 이전 토큰은 더 이상 쓸 수 없다")
    void reissue() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);

        var reissued = reissueMember(oldToken);

        assertThat(reissued.subject().principal()).isEqualTo(member);
        assertThat(reissued.refreshToken()).isNotEqualTo(oldToken);
        assertInvalid(() -> reissueMember(oldToken));
        assertThat(reissueMember(reissued.refreshToken()).subject().principal()).isEqualTo(member);
    }

    @Test
    @DisplayName("행의 주체 종류가 엔드포인트가 기대한 종류와 다르면 재발급하지 않고, 행은 지우지 않는다")
    void principalTypeMismatch() {
        var member = saveMember();
        var token = refreshTokenService.issue(member);

        assertInvalid(() -> refreshTokenService.reissue(PrincipalType.ADMIN, token));

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertThat(reissueMember(token).subject().principal()).isEqualTo(member);
    }

    @Test
    @DisplayName("관리자 토큰 값으로 회원 재발급을 부르면 거절한다")
    void adminTokenOnMemberReissue() {
        var token = refreshTokenService.issue(PrincipalId.admin(1L));

        assertInvalid(() -> reissueMember(token));

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("모르는 토큰이면 재발급하지 않는다")
    void unknownToken() {
        assertInvalid(() -> reissueMember("unknown-token"));
    }

    @Test
    @DisplayName("토큰이 없으면 재발급하지 않는다")
    void missingToken() {
        assertInvalid(() -> reissueMember(null));
    }

    @Test
    @DisplayName("만료된 토큰이면 재발급하지 않는다")
    void expiredToken() {
        var member = saveMember();
        var now = LocalDateTime.now();
        refreshTokenRepository.upsert(member, RefreshToken.hashOf("expired-token"), now.minusSeconds(1), now);

        assertInvalid(() -> reissueMember("expired-token"));
    }

    @Test
    @DisplayName("정지된 회원이면 재발급하지 않고, 행은 지우지 않는다")
    void inactiveMember() {
        var memberId = memberRepository.save(MemberFixture.withStatus(MemberStatus.SUSPENDED)).getId();
        var token = refreshTokenService.issue(PrincipalId.member(memberId));

        assertInvalid(() -> reissueMember(token));

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 회원이면 재발급하지 않고, 행은 지우지 않는다")
    void withdrawnMember() {
        var member = saveMember();
        var token = refreshTokenService.issue(member);

        memberService.withdraw(member.id());

        assertInvalid(() -> reissueMember(token));
        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("교체되고 10초가 지난 직전 토큰이 오면 탈취로 보고 회원의 토큰을 지운다")
    void reusedTokenRevokes() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);
        var newToken = reissueMember(oldToken).refreshToken();
        rotatedSecondsAgo(member, 11);

        assertInvalid(() -> reissueMember(oldToken));

        assertInvalid(() -> reissueMember(newToken));
        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("재사용을 감지하면 그 주체(회원, ID)의 Access Token도 무효화한다(auth.tokens-revoked 발행)")
    void reusedTokenRevokesAccessToken() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);
        reissueMember(oldToken);
        rotatedSecondsAgo(member, 11);

        assertInvalid(() -> reissueMember(oldToken));

        assertThat(events.stream(AuthTokensRevokedEvent.class))
            .singleElement().satisfies(event -> {
                assertThat(event.data().principalType()).isEqualTo(PrincipalType.MEMBER);
                assertThat(event.data().principalId()).isEqualTo(member.id());
            });
    }

    @Test
    @DisplayName("교체 직후 직전 토큰이 오면 동시 재발급으로 보고 거절만 한다")
    void reusedWithinGraceOnlyRejects() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);
        var newToken = reissueMember(oldToken).refreshToken();

        assertInvalid(() -> reissueMember(oldToken));

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertThat(reissueMember(newToken).subject().principal()).isEqualTo(member);
        assertThat(events.stream(AuthTokensRevokedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("다시 로그인한 뒤 옛 토큰이 오면 거절만 하고 새로 받은 토큰은 쓸 수 있다")
    void reusedAfterLoginOnlyRejects() {
        var member = saveMember();
        var oldToken = refreshTokenService.issue(member);
        reissueMember(oldToken);
        rotatedSecondsAgo(member, 11);
        var loginToken = refreshTokenService.issue(member);

        assertInvalid(() -> reissueMember(oldToken));

        assertThat(reissueMember(loginToken).subject().principal()).isEqualTo(member);
    }

    // 어느 쪽이 먼저든 결과는 같다: 한 요청만 교체하고 나머지는 바뀐 해시를 보고 거절된다
    // 거절된 요청은 직전 토큰을 보낸 셈이지만 유예 시간 안이라 토큰을 지우지 않는다
    @Test
    @DisplayName("같은 토큰으로 20개가 동시에 재발급하면 한 요청만 성공하고 그 토큰은 계속 쓸 수 있다")
    void concurrentReissue() throws Exception {
        var member = saveMember();
        var token = refreshTokenService.issue(member);
        var start = new CountDownLatch(1);
        Callable<String> reissue = () -> {
            start.await();
            try {
                return reissueMember(token).refreshToken();
            } catch (BusinessException e) {
                assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
                return null;
            }
        };

        var results = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            var futures = new ArrayList<Future<String>>();
            for (var i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(reissue));
            }
            start.countDown();
            for (var future : futures) {
                results.add(future.get());
            }
        }

        var reissued = results.stream().filter(Objects::nonNull).toList();
        assertThat(reissued).hasSize(1);
        assertThat(reissueMember(reissued.getFirst()).subject().principal()).isEqualTo(member);
    }

    @Test
    @DisplayName("폐기하면 토큰이 지워진다")
    void revoke() {
        var member = saveMember();
        var token = refreshTokenService.issue(member);

        refreshTokenService.revoke(PrincipalType.MEMBER, token);

        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("폐기도 행의 주체 종류가 기대한 종류와 다르면 지우지 않는다")
    void revokeIgnoresOtherPrincipalType() {
        var member = saveMember();
        var token = refreshTokenService.issue(member);

        refreshTokenService.revoke(PrincipalType.ADMIN, token);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
    }

    private PrincipalId saveMember() {
        return PrincipalId.member(memberRepository.save(MemberFixture.kakaoMember()).getId());
    }

    private ReissuedToken reissueMember(String token) {
        return refreshTokenService.reissue(PrincipalType.MEMBER, token);
    }

    // 10초를 기다리지 않도록 교체 시각을 과거로 돌린다
    private void rotatedSecondsAgo(PrincipalId principal, int seconds) {
        jdbcTemplate.update(
            "UPDATE auth.refresh_token SET rotated_at = rotated_at - make_interval(secs => ?) "
                + "WHERE principal_type = ? AND principal_id = ?",
            seconds, principal.type().name(), principal.id());
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
