package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByPreviousTokenHash(String previousTokenHash);

    void upsert(PrincipalId principal, String tokenHash, LocalDateTime expiresAt, LocalDateTime now);

    int rotate(PrincipalId principal, String oldTokenHash, String newTokenHash, LocalDateTime expiresAt,
        LocalDateTime now);

    void deleteByTokenHash(String tokenHash);

    void deleteByPrincipal(PrincipalId principal);

    int deleteExpired(LocalDateTime now);
}
