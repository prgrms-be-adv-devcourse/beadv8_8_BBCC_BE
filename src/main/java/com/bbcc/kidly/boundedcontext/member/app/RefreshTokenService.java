package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.ReissuedToken;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token을 발급 · 재발급 · 폐기한다. 원문은 쿠키로만 내려주고 DB에는 해시만 둔다.
 * Access Token은 {@link AuthTokenService}가 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenProperties properties;

    // 로그인할 때 발급한다. 이미 있으면 새 값으로 바꾸므로 다른 기기의 토큰은 다음 재발급 때 거절된다
    @Transactional
    public String issue(Long memberId) {
        var now = LocalDateTime.now();
        var token = RefreshToken.generate();
        refreshTokenRepository.upsert(memberId, RefreshToken.hashOf(token), now.plus(properties.validity()), now);
        return token;
    }

    // 토큰을 새 값으로 바꾼다. 같은 토큰으로 동시에 요청하면 한 요청만 바꾸고 나머지는 거절한다
    // 재사용을 감지해 지운 것은 401을 던져도 남아야 해서 업무 예외로는 롤백하지 않는다
    @Transactional(noRollbackFor = BusinessException.class)
    public ReissuedToken reissue(String token) {
        if (token == null) {
            throw invalid();
        }
        var now = LocalDateTime.now();
        var tokenHash = RefreshToken.hashOf(token);
        var current = refreshTokenRepository.findByTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.isUsable(now));
        if (current.isEmpty()) {
            revokeIfReused(tokenHash, now);
            throw invalid();
        }
        var saved = current.get();
        var memberId = saved.getMemberId();
        var member = memberRepository.findById(memberId)
            .filter(Member::canLogin)
            .orElseThrow(RefreshTokenService::invalid);
        var newToken = RefreshToken.generate();
        var rotated = refreshTokenRepository.rotate(
            memberId, saved.getTokenHash(), RefreshToken.hashOf(newToken), now.plus(properties.validity()), now);
        if (rotated != 1) {
            log.warn("Refresh Token 재발급 거절: memberId={} reason=concurrent-rotation", memberId);
            throw invalid();
        }
        return new ReissuedToken(member, newToken);
    }

    // 이미 교체된 직전 토큰이 유예 시간 뒤에 오면 탈취로 보고 그 회원의 토큰을 지운다
    private void revokeIfReused(String tokenHash, LocalDateTime now) {
        refreshTokenRepository.findByPreviousTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.isReused(now))
            .ifPresent(refreshToken -> {
                refreshTokenRepository.deleteByMemberId(refreshToken.getMemberId());
                log.warn("Refresh Token 재사용 감지로 폐기: memberId={}", refreshToken.getMemberId());
            });
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.deleteByTokenHash(RefreshToken.hashOf(token));
    }

    private static BusinessException invalid() {
        return new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN);
    }
}
