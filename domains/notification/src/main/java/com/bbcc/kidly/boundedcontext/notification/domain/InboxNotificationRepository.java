package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InboxNotificationRepository {

    /**
     * 같은 dedupKey가 없을 때만 넣는다. 동시에 같은 키로 넣어도 예외 없이 한 건만 남는다. 넣었으면 true.
     */
    boolean saveIfAbsent(InboxNotification notification, LocalDateTime now);

    Optional<InboxNotification> findByIdAndRecipient(Long id, InboxRecipient recipient);

    Page<InboxNotification> findNewestFirst(InboxRecipient recipient, Pageable pageable);

    long countUnread(InboxRecipient recipient);

    int readAllUnread(InboxRecipient recipient, LocalDateTime now);

    int deleteAllOf(InboxRecipient recipient);

    int deleteCreatedBefore(LocalDateTime cutoff);
}
