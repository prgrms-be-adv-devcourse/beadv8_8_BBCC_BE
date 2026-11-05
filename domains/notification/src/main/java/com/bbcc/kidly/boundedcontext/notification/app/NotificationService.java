package com.bbcc.kidly.boundedcontext.notification.app;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 이벤트로 알림을 만든다. 같은 이벤트 · 같은 채널은 한 건만 만들고, 커밋된 뒤에 비동기 발송을 넘긴다.
 * 커밋 전에 넘기면 다른 스레드가 아직 저장되지 않은 알림을 찾지 못한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties notificationProperties;
    private final MemberContactService memberContactService;

    @Transactional
    public void createWelcomeEmail(WelcomeEmailCommand command) {
        var content = WelcomeEmail.compose(notificationProperties.siteUrl());
        createEmail(new EmailNotificationInfo(command.eventId(), command.memberId(), NotificationType.WELCOME,
            command.email(), content.title(), content.body()));
    }

    /**
     * 정산금 지급 메일. 같은 정산서의 지급 요청이 다시 오면 결제가 새 eventId로 지급 완료를 다시 발행하므로,
     * 이벤트 ID 대신 정산서 ID로 만든 고정 키로 한 통만 만든다. 0원 정산은 입금이 없어 보내지 않는다.
     */
    @Transactional
    public void createPayoutEmail(PayoutEmailCommand command) {
        if (command.amount() <= 0) {
            return;
        }
        var key = payoutKey(command.settlementId());
        if (exists(key)) {
            return;
        }
        memberContactService.findEmail(command.sellerId()).ifPresent(email -> {
            var content = PayoutEmail.compose(notificationProperties.siteUrl(), command.settlementId(),
                command.amount());
            createEmail(new EmailNotificationInfo(key, command.sellerId(), NotificationType.PAYOUT_COMPLETED,
                email, content.title(), content.body()));
        });
    }

    // 회원 ID가 없는 예전 환불 이벤트는 받는 사람을 알 수 없어 만들지 않는다
    @Transactional
    public void createRefundEmail(RefundEmailCommand command) {
        if (command.memberId() == null || exists(command.eventId())) {
            return;
        }
        memberContactService.findEmail(command.memberId()).ifPresent(email -> {
            var content = RefundEmail.compose(notificationProperties.siteUrl(), command.orderId(),
                command.amount());
            createEmail(new EmailNotificationInfo(command.eventId(), command.memberId(),
                NotificationType.REFUND_COMPLETED, email, content.title(), content.body()));
        });
    }

    // 정지 사유는 회원 서비스가 200자까지 받으므로 본문 한도(4000자)를 넘지 않는다
    @Transactional
    public void createSuspendedEmail(UUID eventId, Long memberId, String reason) {
        if (exists(eventId)) {
            return;
        }
        memberContactService.findEmail(memberId).ifPresent(email -> {
            var content = SuspendedEmail.compose(reason);
            createEmail(new EmailNotificationInfo(eventId, memberId, NotificationType.MEMBER_SUSPENDED, email,
                content.title(), content.body()));
        });
    }

    // 거절 사유는 회원 서비스가 200자까지 받으므로 본문 한도(4000자)를 넘지 않는다
    @Transactional
    public void createApplicationRejectedEmail(UUID eventId, Long memberId, String reason) {
        if (exists(eventId)) {
            return;
        }
        memberContactService.findEmail(memberId).ifPresent(email -> {
            var content = ApplicationRejectedEmail.compose(reason);
            createEmail(new EmailNotificationInfo(eventId, memberId, NotificationType.SELLER_APPLICATION_REJECTED,
                email, content.title(), content.body()));
        });
    }

    private void createEmail(EmailNotificationInfo info) {
        if (exists(info.eventId())) {
            return;
        }
        var notification = notificationRepository.save(Notification.email(info));
        dispatchAfterCommit(notification.getId());
    }

    private boolean exists(UUID eventId) {
        return notificationRepository.existsByEventIdAndChannel(eventId, NotificationChannel.EMAIL);
    }

    // 정산서 하나에 하나로 정해지는 키. 지급 완료 이벤트의 ID와 겹치지 않게 이름을 붙여 만든다
    static UUID payoutKey(Long settlementId) {
        return UUID.nameUUIDFromBytes(("payment.payout-completed:" + settlementId).getBytes(UTF_8));
    }

    private void dispatchAfterCommit(Long notificationId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationDispatcher.dispatchAsync(notificationId);
            }
        });
    }
}
