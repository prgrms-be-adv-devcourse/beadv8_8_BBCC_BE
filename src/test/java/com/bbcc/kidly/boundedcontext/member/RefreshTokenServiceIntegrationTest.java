package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import com.bbcc.kidly.boundedcontext.member.out.JpaRefreshTokenRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JpaRefreshTokenRepository jpaRefreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

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

    // 어느 쪽이 먼저든 결과는 같다: 한 요청만 교체하고 다른 요청은 바뀐 해시를 보고 거절된다
    @Test
    @DisplayName("같은 토큰으로 동시에 재발급하면 한 요청만 성공한다")
    void concurrentReissue() throws Exception {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var token = refreshTokenService.issue(memberId);
        var start = new CountDownLatch(1);
        Callable<Boolean> reissue = () -> {
            start.await();
            try {
                refreshTokenService.reissue(token);
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(reissue);
            var second = executor.submit(reissue);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    @DisplayName("폐기하면 토큰이 지워진다")
    void revoke() {
        var memberId = memberRepository.save(MemberFixture.kakaoMember()).getId();
        var token = refreshTokenService.issue(memberId);

        refreshTokenService.revoke(token);

        assertThat(jpaRefreshTokenRepository.count()).isZero();
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN));
    }
}
