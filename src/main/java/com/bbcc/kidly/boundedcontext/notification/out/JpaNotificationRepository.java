package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
