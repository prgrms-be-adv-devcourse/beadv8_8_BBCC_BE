package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    @Query("""
        SELECT n.id FROM Notification n
        WHERE (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.FAILED
               AND n.nextAttemptAt <= :now)
           OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.PENDING
               AND n.createdAt < :pendingBefore)
           OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SENDING
               AND n.claimedAt < :sendingBefore)
        ORDER BY n.id
        """)
    List<Long> findRetryTargetIds(@Param("now") LocalDateTime now,
                                  @Param("pendingBefore") LocalDateTime pendingBefore,
                                  @Param("sendingBefore") LocalDateTime sendingBefore,
                                  Pageable pageable);

    // 대량 UPDATE는 엔티티를 거치지 않으므로 수정 시각 · 버전을 직접 바꾸고, 영속성 컨텍스트에 남은 옛 값을 비운다
    @Override
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SKIPPED,
            n.nextAttemptAt = NULL, n.updatedAt = :now, n.version = n.version + 1
        WHERE n.memberId = :memberId
          AND (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.PENDING
               OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.FAILED
                   AND n.nextAttemptAt IS NOT NULL))
        """)
    int skipUnsentOf(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.recipient = NULL, n.body = '', n.updatedAt = :now, n.version = n.version + 1
        WHERE n.memberId = :memberId AND (n.recipient IS NOT NULL OR n.body <> '')
        """)
    int eraseRecipientOf(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    @Override
    @Modifying
    @Query("""
        DELETE FROM Notification n
        WHERE n.createdAt < :cutoff
          AND (n.status IN (com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SENT,
                            com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SKIPPED)
               OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.FAILED
                   AND n.nextAttemptAt IS NULL))
        """)
    int deleteFinishedCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Override
    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE n.status IN (com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.PENDING,
                           com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SENDING)
           OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.FAILED
               AND n.nextAttemptAt IS NOT NULL)
        """)
    long countUnsent();

    @Override
    @Query("""
        SELECT MIN(n.createdAt) FROM Notification n
        WHERE n.status IN (com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.PENDING,
                           com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.SENDING)
           OR (n.status = com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus.FAILED
               AND n.nextAttemptAt IS NOT NULL)
        """)
    Optional<LocalDateTime> findOldestUnsentCreatedAt();
}
