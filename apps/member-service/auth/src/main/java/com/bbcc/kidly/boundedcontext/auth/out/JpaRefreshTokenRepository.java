package com.bbcc.kidly.boundedcontext.auth.out;

import com.bbcc.kidly.boundedcontext.auth.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenId;
import com.bbcc.kidly.boundedcontext.auth.domain.RefreshTokenRepository;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, RefreshTokenId>, RefreshTokenRepository {

    // 네이티브 쿼리라 JPA Auditing이 동작하지 않으므로 created_at · updated_at을 직접 넣는다
    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO auth.refresh_token (principal_type, principal_id, token_hash, expires_at,
            previous_token_hash, rotated_at, created_at, updated_at)
        VALUES (:#{#principal.type().name()}, :#{#principal.id()}, :tokenHash, :expiresAt, NULL, NULL, :now, :now)
        ON CONFLICT (principal_type, principal_id) DO UPDATE
        SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at,
            previous_token_hash = NULL, rotated_at = NULL, updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("principal") PrincipalId principal, @Param("tokenHash") String tokenHash,
        @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    // 같은 토큰으로 동시에 재발급하면 뒤의 UPDATE는 앞의 커밋을 기다렸다가 바뀐 해시를 보고 0행을 바꾼다
    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken t
        SET t.tokenHash = :newTokenHash, t.expiresAt = :expiresAt,
            t.previousTokenHash = :oldTokenHash, t.rotatedAt = :now, t.updatedAt = :now
        WHERE t.id.type = :#{#principal.type()} AND t.id.principalId = :#{#principal.id()}
            AND t.tokenHash = :oldTokenHash
        """)
    int rotate(@Param("principal") PrincipalId principal, @Param("oldTokenHash") String oldTokenHash,
        @Param("newTokenHash") String newTokenHash, @Param("expiresAt") LocalDateTime expiresAt,
        @Param("now") LocalDateTime now);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Override
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        DELETE FROM RefreshToken t
        WHERE t.id.type = :#{#principal.type()} AND t.id.principalId = :#{#principal.id()}
        """)
    void deleteByPrincipal(@Param("principal") PrincipalId principal);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
