package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함. 알림을 넣고, 회원이 자기 알림을 보고 읽음 처리한다. 오래된 알림은 정리한다.
 */
@Service
@RequiredArgsConstructor
public class InboxNotificationService {

    private final InboxNotificationRepository inboxNotificationRepository;

    /**
     * 알림함에 넣는다. 같은 dedupKey가 이미 있으면 넣지 않는다(같은 이벤트를 다시 받거나 동시에 받아도 한 건).
     */
    @Transactional
    public void add(InboxNotificationInfo info) {
        inboxNotificationRepository.saveIfAbsent(InboxNotification.create(info), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Page<InboxNotificationView> findMine(Long memberId, int page, int size) {
        return inboxNotificationRepository
            .findByMemberIdOrderByCreatedAtDescIdDesc(memberId, PageRequest.of(page, size))
            .map(InboxNotificationView::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long memberId) {
        return inboxNotificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    /**
     * 읽음 처리. 이미 읽었으면 그대로 둔다. 없거나 남의 알림이면 NOTIFICATION_NOT_FOUND.
     */
    @Transactional
    public void read(Long memberId, Long notificationId) {
        inboxNotificationRepository.findByIdAndMemberId(notificationId, memberId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
            .read(LocalDateTime.now());
    }

    /**
     * 내 안 읽은 알림을 모두 읽음 처리. 이미 읽은 알림의 읽은 시각은 그대로 둔다. 안 읽은 알림이 없어도 된다.
     */
    @Transactional
    public void readAll(Long memberId) {
        inboxNotificationRepository.readAllUnread(memberId, LocalDateTime.now());
    }

    /**
     * 보존 기간(NotificationPolicy.INBOX_RETENTION)이 지난 알림을 지운다. 지운 개수.
     */
    @Transactional
    public int deleteExpired() {
        return inboxNotificationRepository.deleteCreatedBefore(
            LocalDateTime.now().minus(NotificationPolicy.INBOX_RETENTION));
    }
}
