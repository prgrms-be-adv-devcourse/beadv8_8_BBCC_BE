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

    Optional<InboxNotification> findByIdAndMemberId(Long id, Long memberId);

    // 최신순. 같은 시각이면 나중에 넣은 것부터
    Page<InboxNotification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadAtIsNull(Long memberId);
}
