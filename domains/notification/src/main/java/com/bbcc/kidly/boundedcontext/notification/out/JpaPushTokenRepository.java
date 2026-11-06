package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.PushToken;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JpaPushTokenRepository extends JpaRepository<PushToken, Long>, PushTokenRepository {

    // 찾은 뒤 저장하면 같은 FID가 동시에 오면 유니크 위반 예외가 난다. DB가 넣거나 고치게 한다.
    // 네이티브라 생성 · 수정 시각을 직접 넣는다
    @Override
    @Modifying
    @Query(value = """
        INSERT INTO notification.push_token (member_id, fid, last_registered_at, created_at, updated_at)
        VALUES (:memberId, :fid, :now, :now, :now)
        ON CONFLICT (fid) DO UPDATE
            SET member_id = EXCLUDED.member_id,
                last_registered_at = EXCLUDED.last_registered_at,
                updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void register(Long memberId, String fid, LocalDateTime now);

    @Override
    @Modifying
    @Query(value = """
        DELETE FROM notification.push_token
        WHERE member_id = :memberId
          AND id NOT IN (SELECT id FROM notification.push_token
                         WHERE member_id = :memberId
                         ORDER BY last_registered_at DESC, id DESC
                         LIMIT :keep)
        """, nativeQuery = true)
    int deleteAllButNewest(Long memberId, int keep);

    @Override
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.memberId = :memberId AND t.fid = :fid")
    int deleteByMemberIdAndFid(Long memberId, String fid);

    @Override
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.memberId = :memberId")
    int deleteByMemberId(Long memberId);

    @Override
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.fid IN :fids")
    int deleteByFidIn(Collection<String> fids);

    @Override
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.lastRegisteredAt < :cutoff")
    int deleteRegisteredBefore(LocalDateTime cutoff);

    @Override
    @Query("SELECT t.fid FROM PushToken t WHERE t.memberId = :memberId ORDER BY t.lastRegisteredAt DESC, t.id DESC")
    List<String> findFidsOf(Long memberId);
}
