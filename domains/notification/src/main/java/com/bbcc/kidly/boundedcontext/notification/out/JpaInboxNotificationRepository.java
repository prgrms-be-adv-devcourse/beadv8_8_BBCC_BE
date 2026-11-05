package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JpaInboxNotificationRepository
    extends JpaRepository<InboxNotification, Long>, InboxNotificationRepository {

    // 있는지 확인한 뒤 저장하면 동시에 같은 키가 오면 유니크 위반 예외가 난다. 트랜잭션 안에서 그 예외를 잡으면
    // 영속성 컨텍스트가 망가지므로, DB가 충돌을 건너뛰게 한다. 네이티브라 생성 · 수정 시각을 직접 넣는다
    @Override
    default boolean saveIfAbsent(InboxNotification notification, LocalDateTime now) {
        return insertIgnoringDuplicate(notification.getMemberId(), notification.getType().name(),
            notification.getTitle(), notification.getBody(), notification.getLinkPath(), notification.getDedupKey(),
            now) == 1;
    }

    @Modifying
    @Query(value = """
        INSERT INTO notification.inbox (member_id, type, title, body, link_path, dedup_key, created_at, updated_at)
        VALUES (:memberId, :type, :title, :body, :linkPath, :dedupKey, :now, :now)
        ON CONFLICT (dedup_key) DO NOTHING
        """, nativeQuery = true)
    int insertIgnoringDuplicate(Long memberId, String type, String title, String body, String linkPath,
        String dedupKey, LocalDateTime now);
}
