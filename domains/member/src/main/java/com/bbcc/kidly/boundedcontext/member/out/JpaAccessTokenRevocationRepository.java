package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocation;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface JpaAccessTokenRevocationRepository
    extends JpaRepository<AccessTokenRevocation, Long>, AccessTokenRevocationRepository {

    // 네이티브 쿼리라 JPA Auditing이 동작하지 않으므로 created_at · updated_at을 직접 넣는다
    @Override
    @Transactional
    @Modifying
    @Query(value = """
        INSERT INTO member.access_token_revocation (member_id, revoked_at, created_at, updated_at)
        VALUES (:memberId, :revokedAt, :now, :now)
        ON CONFLICT (member_id) DO UPDATE
        SET revoked_at = GREATEST(access_token_revocation.revoked_at, EXCLUDED.revoked_at),
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("revokedAt") LocalDateTime revokedAt,
        @Param("now") LocalDateTime now);

    @Override
    @Query("SELECT r FROM AccessTokenRevocation r WHERE r.revokedAt > :since")
    List<AccessTokenRevocation> findAllRevokedAfter(@Param("since") LocalDateTime since);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AccessTokenRevocation r WHERE r.revokedAt < :before")
    int deleteRevokedBefore(@Param("before") LocalDateTime before);
}
