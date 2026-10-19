package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    /**
     * 다시 보낼 알림의 ID. 다음 시도 시각이 된 FAILED, {@code pendingBefore} 전에 만들어져 아직 PENDING인 것,
     * {@code sendingBefore} 전에 맡아 아직 SENDING인 것. ID 순서.
     */
    List<Long> findRetryTargetIds(LocalDateTime now, LocalDateTime pendingBefore, LocalDateTime sendingBefore,
                                  Pageable pageable);
}
