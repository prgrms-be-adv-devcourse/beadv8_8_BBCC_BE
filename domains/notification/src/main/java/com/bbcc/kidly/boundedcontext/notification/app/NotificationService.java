package com.bbcc.kidly.boundedcontext.notification.app;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
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
 * 계정 정지를 뺀 메일은 같은 일을 받는 사람 알림함에도 한 건 남긴다(같은 트랜잭션). 알림함은 이메일이 필요 없어
 * 이메일 복사본이 없어 메일을 못 만들어도 남긴다. 계정 정지는 로그인이 막혀 알림함을 볼 수 없어 메일만 만든다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String REJECTED_INBOX_BODY = "보내 주신 판매자 신청이 거절됐어요.";

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties notificationProperties;
    private final MemberContactService memberContactService;
    private final InboxNotificationService inboxNotificationService;

    @Transactional
    public void createWelcomeEmail(WelcomeEmailCommand command) {
        inboxNotificationService.add(new InboxNotificationInfo(command.memberId(), NotificationType.WELCOME,
            "Kidly에 오신 것을 환영해요", "자녀의 키와 체형을 등록하면 지금 잘 맞는 사이즈의 옷을 골라 드려요.", null,
            "member.registered:" + command.eventId()));
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
        inboxNotificationService.add(new InboxNotificationInfo(command.sellerId(), NotificationType.PAYOUT_COMPLETED,
            "정산금이 입금됐어요", "정산금 %,d원이 예치금으로 입금됐어요.".formatted(command.amount()),
            "/center/settlements/" + command.settlementId(), "payment.payout-completed:" + command.settlementId()));
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

    // 주문 결제완료는 주문마다 한 번만 나가므로 이벤트 ID로 한 통만 만든다
    @Transactional
    public void createOrderPaidEmail(OrderPaidCommand command) {
        inboxNotificationService.add(new InboxNotificationInfo(command.memberId(), NotificationType.ORDER_PAID,
            "주문이 결제됐어요", orderPaidInboxBody(command), "/orders/" + command.orderId(),
            "order.paid:" + command.orderId()));
        if (exists(command.eventId())) {
            return;
        }
        memberContactService.findEmail(command.memberId()).ifPresent(email -> {
            var content = OrderPaidEmail.compose(notificationProperties.siteUrl(), command);
            createEmail(new EmailNotificationInfo(command.eventId(), command.memberId(), NotificationType.ORDER_PAID,
                email, content.title(), content.body()));
        });
    }

    // 회원 ID가 없는 예전 환불 이벤트는 받는 사람을 알 수 없어 만들지 않는다
    @Transactional
    public void createRefundEmail(RefundEmailCommand command) {
        if (command.memberId() == null) {
            return;
        }
        inboxNotificationService.add(new InboxNotificationInfo(command.memberId(), NotificationType.REFUND_COMPLETED,
            "환불이 완료됐어요", "취소한 주문의 %,d원을 예치금으로 돌려드렸어요.".formatted(command.amount()),
            "/orders/" + command.orderId(), "payment.refunded:" + command.eventId()));
        if (exists(command.eventId())) {
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
        var hasReason = reason != null && !reason.isBlank();
        inboxNotificationService.add(new InboxNotificationInfo(memberId, NotificationType.SELLER_APPLICATION_REJECTED,
            "판매자 신청이 거절됐어요", hasReason ? REJECTED_INBOX_BODY + " 거절 사유: " + reason.strip() : REJECTED_INBOX_BODY,
            null, "seller.application-rejected:" + eventId));
        if (exists(eventId)) {
            return;
        }
        memberContactService.findEmail(memberId).ifPresent(email -> {
            var content = ApplicationRejectedEmail.compose(reason);
            createEmail(new EmailNotificationInfo(eventId, memberId, NotificationType.SELLER_APPLICATION_REJECTED,
                email, content.title(), content.body()));
        });
    }

    @Transactional
    public void createSellerApprovedEmail(UUID eventId, Long sellerId, String businessName) {
        inboxNotificationService.add(new InboxNotificationInfo(sellerId, NotificationType.SELLER_APPROVED,
            "판매자 신청이 승인됐어요", "%s 상호의 판매자 신청이 승인됐어요. 판매자 센터에서 상품을 등록해 보세요.".formatted(businessName),
            "/center", "seller.changed:" + eventId));
        if (exists(eventId)) {
            return;
        }
        memberContactService.findEmail(sellerId).ifPresent(email -> {
            var content = SellerApprovedEmail.compose(notificationProperties.siteUrl(), businessName);
            createEmail(new EmailNotificationInfo(eventId, sellerId, NotificationType.SELLER_APPROVED, email,
                content.title(), content.body()));
        });
    }

    @Transactional
    public void createSellerSuspendedEmail(UUID eventId, Long sellerId, String businessName) {
        inboxNotificationService.add(new InboxNotificationInfo(sellerId, NotificationType.SELLER_SUSPENDED,
            "판매자 자격이 정지됐어요", "관리자가 %s 상호의 판매자 자격을 정지했어요.".formatted(businessName), null,
            "seller.changed:" + eventId));
        if (exists(eventId)) {
            return;
        }
        memberContactService.findEmail(sellerId).ifPresent(email -> {
            var content = SellerSuspendedEmail.compose(businessName);
            createEmail(new EmailNotificationInfo(eventId, sellerId, NotificationType.SELLER_SUSPENDED, email,
                content.title(), content.body()));
        });
    }

    private void createEmail(EmailNotificationInfo info) {
        if (exists(info.eventId())) {
            return;
        }
        var notification = notificationRepository.save(Notification.email(info));
        dispatchAfterCommit(notification.getId());
    }

    // 알림함 본문은 1000자까지라 첫 상품 이름(255자까지)만 적고 나머지는 "외 N건"으로 줄인다
    private static String orderPaidInboxBody(OrderPaidCommand command) {
        var items = command.items();
        var total = "총 결제 금액은 %,d원이에요.".formatted(command.totalAmount());
        // 주문은 상품 없이 만들어지지 않지만, 받는 쪽은 상품이 빈 메시지도 실패시키지 않는다
        if (items.isEmpty()) {
            return "주문의 결제가 끝났어요. " + total;
        }
        var first = items.getFirst().productName();
        var name = items.size() == 1 ? first : "%s 외 %d건".formatted(first, items.size() - 1);
        return "%s 주문의 결제가 끝났어요. %s".formatted(name, total);
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
