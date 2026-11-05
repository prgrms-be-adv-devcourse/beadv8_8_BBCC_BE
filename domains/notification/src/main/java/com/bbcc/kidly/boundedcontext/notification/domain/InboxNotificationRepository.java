package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InboxNotificationRepository {

    InboxNotification save(InboxNotification notification);

    boolean existsByDedupKey(String dedupKey);

    Optional<InboxNotification> findByIdAndMemberId(Long id, Long memberId);

    // 최신순. 같은 시각이면 나중에 넣은 것부터
    Page<InboxNotification> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);

    long countByMemberIdAndReadAtIsNull(Long memberId);
}
