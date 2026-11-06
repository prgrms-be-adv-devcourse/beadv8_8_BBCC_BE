package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함. 알림을 넣고, 로그인 주체(회원 · 자녀 계정)가 자기 알림을 보고 읽음 처리한다. 오래된 알림은 정리한다.
 * 로그인 주체(PrincipalId)를 받는 사람(InboxRecipient)으로 바꿔 늘 종류와 ID로 거른다. 같은 숫자 ID의 다른 종류 주체 알림은 보이지도 바뀌지도 않는다.
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
    public Page<InboxNotificationView> findMine(PrincipalId principal, int page, int size) {
        return inboxNotificationRepository.findNewestFirst(InboxRecipient.of(principal), PageRequest.of(page, size))
            .map(InboxNotificationView::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(PrincipalId principal) {
        return inboxNotificationRepository.countUnread(InboxRecipient.of(principal));
    }

    /**
     * 읽음 처리. 이미 읽었으면 그대로 둔다. 없거나 남의 알림이면 NOTIFICATION_NOT_FOUND.
     */
    @Transactional
    public void read(PrincipalId principal, Long notificationId) {
        inboxNotificationRepository.findByIdAndRecipient(notificationId, InboxRecipient.of(principal))
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
            .read(LocalDateTime.now());
    }

    /**
     * 내 안 읽은 알림을 모두 읽음 처리. 이미 읽은 알림의 읽은 시각은 그대로 둔다. 안 읽은 알림이 없어도 된다.
     */
    @Transactional
    public void readAll(PrincipalId principal) {
        inboxNotificationRepository.readAllUnread(InboxRecipient.of(principal), LocalDateTime.now());
    }

    /**
     * 이 받는 사람의 알림을 모두 지운다(자녀 계정이 지워졌을 때). 없어도 된다.
     */
    @Transactional
    public void deleteAllOf(InboxRecipient recipient) {
        inboxNotificationRepository.deleteAllOf(recipient);
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
