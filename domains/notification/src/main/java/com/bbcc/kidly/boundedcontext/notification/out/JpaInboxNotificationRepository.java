package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipientType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JpaInboxNotificationRepository
    extends JpaRepository<InboxNotification, Long>, InboxNotificationRepository {

    // 있는지 확인한 뒤 저장하면 동시에 같은 키가 오면 유니크 위반 예외가 난다. 트랜잭션 안에서 그 예외를 잡으면
    // 영속성 컨텍스트가 망가지므로, DB가 충돌을 건너뛰게 한다. 네이티브라 생성 · 수정 시각을 직접 넣는다
    @Override
    default boolean saveIfAbsent(InboxNotification notification, LocalDateTime now) {
        return insertIgnoringDuplicate(notification.getRecipientType().name(), notification.getRecipientId(),
            notification.getType().name(), notification.getTitle(), notification.getBody(),
            notification.getLinkPath(), notification.getDedupKey(), now) == 1;
    }

    @Modifying
    @Query(value = """
        INSERT INTO notification.inbox
            (recipient_type, recipient_id, type, title, body, link_path, dedup_key, created_at, updated_at)
        VALUES (:recipientType, :recipientId, :type, :title, :body, :linkPath, :dedupKey, :now, :now)
        ON CONFLICT (dedup_key) DO NOTHING
        """, nativeQuery = true)
    int insertIgnoringDuplicate(String recipientType, Long recipientId, String type, String title, String body,
        String linkPath, String dedupKey, LocalDateTime now);

    @Override
    default Optional<InboxNotification> findByIdAndRecipient(Long id, InboxRecipient recipient) {
        return findByIdAndRecipientTypeAndRecipientId(id, recipient.type(), recipient.id());
    }

    Optional<InboxNotification> findByIdAndRecipientTypeAndRecipientId(Long id, InboxRecipientType recipientType,
        Long recipientId);

    @Override
    default Page<InboxNotification> findNewestFirst(InboxRecipient recipient, Pageable pageable) {
        return findByRecipientTypeAndRecipientIdOrderByCreatedAtDescIdDesc(recipient.type(), recipient.id(),
            pageable);
    }

    Page<InboxNotification> findByRecipientTypeAndRecipientIdOrderByCreatedAtDescIdDesc(
        InboxRecipientType recipientType, Long recipientId, Pageable pageable);

    @Override
    default long countUnread(InboxRecipient recipient) {
        return countByRecipientTypeAndRecipientIdAndReadAtIsNull(recipient.type(), recipient.id());
    }

    long countByRecipientTypeAndRecipientIdAndReadAtIsNull(InboxRecipientType recipientType, Long recipientId);

    @Override
    default int readAllUnread(InboxRecipient recipient, LocalDateTime now) {
        return readAllUnread(recipient.type(), recipient.id(), now);
    }

    // 한 번의 UPDATE. 엔티티를 거치지 않으므로 수정 시각도 직접 넣는다
    @Modifying
    @Query("""
        UPDATE InboxNotification n SET n.readAt = :now, n.updatedAt = :now
        WHERE n.recipientType = :recipientType AND n.recipientId = :recipientId AND n.readAt IS NULL
        """)
    int readAllUnread(InboxRecipientType recipientType, Long recipientId, LocalDateTime now);

    @Override
    default int deleteAllOf(InboxRecipient recipient) {
        return deleteAllOf(recipient.type(), recipient.id());
    }

    @Modifying
    @Query("""
        DELETE FROM InboxNotification n
        WHERE n.recipientType = :recipientType AND n.recipientId = :recipientId
        """)
    int deleteAllOf(InboxRecipientType recipientType, Long recipientId);

    @Override
    @Modifying
    @Query("DELETE FROM InboxNotification n WHERE n.createdAt < :cutoff")
    int deleteCreatedBefore(LocalDateTime cutoff);
}
