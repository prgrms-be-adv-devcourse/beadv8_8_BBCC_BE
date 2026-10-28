package com.bbcc.kidly.boundedcontext.member.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByPreviousTokenHash(String previousTokenHash);

    // 회원의 토큰을 새 해시로 저장한다. 없으면 만들고 있으면 바꾼다. 동시에 호출해도 한 행만 남는다
    // 로그인은 새로 시작하는 것이라 직전 해시와 교체 시각은 비운다
    void upsert(Long memberId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now);

    // 저장된 해시가 oldTokenHash일 때만 바꾸고 바뀐 행 수(0 또는 1)를 돌려준다
    // 재사용을 감지할 수 있게 oldTokenHash와 교체 시각을 남긴다
    int rotate(Long memberId, String oldTokenHash, String newTokenHash, LocalDateTime expiresAt, LocalDateTime now);

    void deleteByTokenHash(String tokenHash);

    void deleteByMemberId(Long memberId);

    // 지운 행 수를 돌려준다
    int deleteExpired(LocalDateTime now);
}
