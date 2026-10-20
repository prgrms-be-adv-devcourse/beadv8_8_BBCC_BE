package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.member.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Instant;
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

@IntegrationTest
class RefreshTokenServiceIntegrationTest {

    private static final int THREAD_COUNT = 20;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccessTokenRevocationService accessTokenRevocationService;

    @Test
    @DisplayName("발급하면 원문은 돌려주고 DB에는 14일짜리 해시만 저장한다")
    void issue() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();

        var token = refreshTokenService.issue(memberId);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(refreshTokenRepository.findByTokenHash(RefreshToken.hashOf(token)))
            .hasValueSatisfying(saved -> {
                assertThat(saved.getMemberId()).isEqualTo(memberId);
                assertThat(saved.getExpiresAt())
                    .isCloseTo(LocalDateTime.now().plusDays(14), within(1, ChronoUnit.MINUTES));
            });
    }

    @Test
    @DisplayName("다시 로그인하면 토큰이 바뀌어 이전 토큰으로는 재발급할 수 없다")
    void issueAgainReplaces() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);

        refreshTokenService.issue(memberId);

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertInvalid(() -> refreshTokenService.reissue(oldToken));
    }

    @Test
    @DisplayName("재발급하면 새 토큰을 주고 이전 토큰은 더 이상 쓸 수 없다")
    void reissue() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);

        var reissued = refreshTokenService.reissue(oldToken);

        assertThat(reissued.member().getId()).isEqualTo(memberId);
        assertThat(reissued.refreshToken()).isNotEqualTo(oldToken);
        assertInvalid(() -> refreshTokenService.reissue(oldToken));
        assertThat(refreshTokenService.reissue(reissued.refreshToken()).member().getId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("모르는 토큰이면 재발급하지 않는다")
    void unknownToken() {
        assertInvalid(() -> refreshTokenService.reissue("unknown-token"));
    }

    @Test
    @DisplayName("토큰이 없으면 재발급하지 않는다")
    void missingToken() {
        assertInvalid(() -> refreshTokenService.reissue(null));
    }

    @Test
    @DisplayName("만료된 토큰이면 재발급하지 않는다")
    void expiredToken() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var now = LocalDateTime.now();
        refreshTokenRepository.upsert(memberId, RefreshToken.hashOf("expired-token"), now.minusSeconds(1), now);

        assertInvalid(() -> refreshTokenService.reissue("expired-token"));
    }

    @Test
    @DisplayName("정지된 회원이면 재발급하지 않는다")
    void inactiveMember() {
        var memberId = memberRepository.save(MemberFixture.withStatus(MemberStatus.SUSPENDED)).getId();
        var token = refreshTokenService.issue(memberId);

        assertInvalid(() -> refreshTokenService.reissue(token));
    }

    @Test
    @DisplayName("교체되고 10초가 지난 직전 토큰이 오면 탈취로 보고 회원의 토큰을 지운다")
    void reusedTokenRevokes() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);
        var newToken = refreshTokenService.reissue(oldToken).refreshToken();
        rotatedSecondsAgo(memberId, 11);

        assertInvalid(() -> refreshTokenService.reissue(oldToken));

        assertInvalid(() -> refreshTokenService.reissue(newToken));
        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("재사용을 감지하면 이미 발급된 Access Token도 바로 무효화한다")
    void reusedTokenRevokesAccessToken() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);
        refreshTokenService.reissue(oldToken);
        rotatedSecondsAgo(memberId, 11);

        assertInvalid(() -> refreshTokenService.reissue(oldToken));

        assertThat(accessTokenRevocationService.isRevoked(memberId, Instant.now().minusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("교체 직후 직전 토큰이 오면 동시 재발급으로 보고 거절만 한다")
    void reusedWithinGraceOnlyRejects() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);
        var newToken = refreshTokenService.reissue(oldToken).refreshToken();

        assertInvalid(() -> refreshTokenService.reissue(oldToken));

        assertThat(jpaRefreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenService.reissue(newToken).member().getId()).isEqualTo(memberId);
        assertThat(accessTokenRevocationService.isRevoked(memberId, Instant.now().minusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("다시 로그인한 뒤 옛 토큰이 오면 거절만 하고 새로 받은 토큰은 쓸 수 있다")
    void reusedAfterLoginOnlyRejects() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var oldToken = refreshTokenService.issue(memberId);
        refreshTokenService.reissue(oldToken);
        rotatedSecondsAgo(memberId, 11);
        var loginToken = refreshTokenService.issue(memberId);

        assertInvalid(() -> refreshTokenService.reissue(oldToken));

        assertThat(refreshTokenService.reissue(loginToken).member().getId()).isEqualTo(memberId);
    }

    // 어느 쪽이 먼저든 결과는 같다: 한 요청만 교체하고 나머지는 바뀐 해시를 보고 거절된다
    // 거절된 요청은 직전 토큰을 보낸 셈이지만 유예 시간 안이라 토큰을 지우지 않는다
    @Test
    @DisplayName("같은 토큰으로 20개가 동시에 재발급하면 한 요청만 성공하고 그 토큰은 계속 쓸 수 있다")
    void concurrentReissue() throws Exception {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var token = refreshTokenService.issue(memberId);
        var start = new CountDownLatch(1);
        Callable<String> reissue = () -> {
            start.await();
            try {
                return refreshTokenService.reissue(token).refreshToken();
            } catch (BusinessException e) {
                assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN);
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
        assertThat(refreshTokenService.reissue(reissued.getFirst()).member().getId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("폐기하면 토큰이 지워진다")
    void revoke() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var token = refreshTokenService.issue(memberId);

        refreshTokenService.revoke(token);

        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    // 10초를 기다리지 않도록 교체 시각을 과거로 돌린다
    private void rotatedSecondsAgo(Long memberId, int seconds) {
        jdbcTemplate.update(
            "UPDATE member.refresh_token SET rotated_at = rotated_at - make_interval(secs => ?) WHERE member_id = ?",
            seconds, memberId);
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN));
    }
}
