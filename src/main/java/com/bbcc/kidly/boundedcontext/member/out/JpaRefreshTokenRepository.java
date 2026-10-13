package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import com.bbcc.kidly.boundedcontext.member.domain.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, Long>, RefreshTokenRepository {

    // 네이티브 쿼리라 JPA Auditing이 동작하지 않으므로 created_at · updated_at을 직접 넣는다
    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO member.refresh_token (member_id, token_hash, expires_at, created_at, updated_at)
        VALUES (:memberId, :tokenHash, :expiresAt, :now, :now)
        ON CONFLICT (member_id) DO UPDATE
        SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at, updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("tokenHash") String tokenHash,
        @Param("expiresAt") LocalDateTime expiresAt, @Param("now") LocalDateTime now);

    // 같은 토큰으로 동시에 재발급하면 뒤의 UPDATE는 앞의 커밋을 기다렸다가 바뀐 해시를 보고 0행을 바꾼다
    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken t
        SET t.tokenHash = :newTokenHash, t.expiresAt = :expiresAt, t.updatedAt = :now
        WHERE t.memberId = :memberId AND t.tokenHash = :oldTokenHash
        """)
    int rotate(@Param("memberId") Long memberId, @Param("oldTokenHash") String oldTokenHash,
        @Param("newTokenHash") String newTokenHash, @Param("expiresAt") LocalDateTime expiresAt,
        @Param("now") LocalDateTime now);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
