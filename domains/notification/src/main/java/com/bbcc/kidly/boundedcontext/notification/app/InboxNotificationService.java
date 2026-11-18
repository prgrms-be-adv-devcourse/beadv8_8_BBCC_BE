package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.InboxNotificationView;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipientType;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.WithdrawnMemberRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.auth.PrincipalId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 숫자 ID의 다른 종류 주체 알림은 보이지도 바뀌지도 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxNotificationService {

    private final InboxNotificationRepository inboxNotificationRepository;
    private final WithdrawnMemberRepository withdrawnMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 같은 dedupKey가 이미 있으면 넣지 않는다(같은 이벤트를 다시 받거나 동시에 받아도 한 건).
     * 탈퇴한 회원에게는 넣지 않는다. 탈퇴 뒤에 늦게 온 이벤트(판매자 탈퇴로 취소된 주문 등)가 지운 알림함을 되살리지 않게 한다.
     * 탈퇴 처리(MemberWithdrawalService.purge)와 같은 회원 잠금을 잡고 탈퇴 표시를 보므로, 탈퇴와 엇갈려도 행이 남지 않는다.
     */
    @Transactional
    public void add(InboxNotificationInfo info) {
        var recipient = info.recipient();
        if (recipient.type() == InboxRecipientType.MEMBER) {
            withdrawnMemberRepository.lock(recipient.id());
            if (withdrawnMemberRepository.exists(recipient.id())) {
                log.debug("탈퇴한 회원이라 알림함에 넣지 않음: type={}", info.type());
                return;
            }
        }
        if (inboxNotificationRepository.saveIfAbsent(InboxNotification.create(info), LocalDateTime.now())) {
            eventPublisher.publishEvent(InboxChanged.of(recipient));
        }
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

    @Transactional
    public void read(PrincipalId principal, Long notificationId) {
        inboxNotificationRepository.findByIdAndRecipient(notificationId, InboxRecipient.of(principal))
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
            .read(LocalDateTime.now());
        eventPublisher.publishEvent(new InboxChanged(principal));
    }

    @Transactional
    public void readAll(PrincipalId principal) {
        if (inboxNotificationRepository.readAllUnread(InboxRecipient.of(principal), LocalDateTime.now()) > 0) {
            eventPublisher.publishEvent(new InboxChanged(principal));
        }
    }

    @Transactional
    public void deleteAllOf(InboxRecipient recipient) {
        inboxNotificationRepository.deleteAllOf(recipient);
    }

    @Transactional
    public int deleteExpired() {
        return inboxNotificationRepository.deleteCreatedBefore(
            LocalDateTime.now().minus(NotificationPolicy.INBOX_RETENTION));
    }
}
