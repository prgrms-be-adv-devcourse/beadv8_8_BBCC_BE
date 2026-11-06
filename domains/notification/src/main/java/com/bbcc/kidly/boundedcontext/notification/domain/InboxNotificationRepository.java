package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 알림함 저장소. 받는 사람으로 거르는 메서드는 모두 종류와 ID를 함께 받는다(InboxRecipient).
 */
public interface InboxNotificationRepository {

    /**
     * 같은 dedupKey가 없을 때만 넣는다. 동시에 같은 키로 넣어도 예외 없이 한 건만 남는다. 넣었으면 true.
     */
    boolean saveIfAbsent(InboxNotification notification, LocalDateTime now);

    Optional<InboxNotification> findByIdAndRecipient(Long id, InboxRecipient recipient);

    // 최신순. 같은 시각이면 나중에 넣은 것부터
    Page<InboxNotification> findNewestFirst(InboxRecipient recipient, Pageable pageable);

    long countUnread(InboxRecipient recipient);

    // 안 읽은 알림을 모두 읽음으로 바꾼다. 바꾼 개수
    int readAllUnread(InboxRecipient recipient, LocalDateTime now);

    // 이 받는 사람의 알림을 읽음 여부와 상관없이 모두 지운다. 지운 개수
    int deleteAllOf(InboxRecipient recipient);

    // 이 시각보다 먼저 만든 알림을 읽음 여부 · 받는 사람과 상관없이 지운다. 지운 개수
    int deleteCreatedBefore(LocalDateTime cutoff);
}
