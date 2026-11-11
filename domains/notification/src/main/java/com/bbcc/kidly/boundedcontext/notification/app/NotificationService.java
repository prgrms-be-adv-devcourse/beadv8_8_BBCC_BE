package com.bbcc.kidly.boundedcontext.notification.app;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipientType;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationPolicy;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.domain.PushNotificationInfo;
import java.time.LocalDateTime;
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
 * 회원이 사이트를 떠난 뒤 일어나는 일(주문 확인 · 주문 취소 · 배송 · 새 주문)은 같은 알림함 알림을 웹 푸시로도 보낸다(createPush).
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.WELCOME, "Kidly에 오신 것을 환영해요", "자녀의 키와 체형을 등록하면 지금 잘 맞는 사이즈의 옷을 골라 드려요.", null,
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.sellerId()),
            NotificationType.PAYOUT_COMPLETED, "정산금이 입금됐어요", "정산금 %,d원이 예치금으로 입금됐어요.".formatted(command.amount()),
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

    // 주문 결제완료는 주문마다 한 번만 나가므로 이벤트 ID로 한 통만 만든다. 알림함과 같은 내용을 푸시로도 보낸다
    @Transactional
    public void createOrderPaidEmail(OrderPaidCommand command) {
        var inbox = new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.ORDER_PAID, "주문이 결제됐어요", orderPaidInboxBody(command), "/orders/" + command.orderId(),
            "order.paid:" + command.orderId());
        inboxNotificationService.add(inbox);
        createPush(inbox);
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.REFUND_COMPLETED, "환불이 완료됐어요", "취소한 주문의 %,d원을 예치금으로 돌려드렸어요.".formatted(command.amount()),
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(memberId),
            NotificationType.SELLER_APPLICATION_REJECTED,
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(sellerId),
            NotificationType.SELLER_APPROVED,
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
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(sellerId),
            NotificationType.SELLER_SUSPENDED,
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

    @Transactional
    public void createProductStoppedByAdminEmail(ProductChangedCommand command) {
        var link = "/center/products/" + command.productId();
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.sellerId()),
            NotificationType.PRODUCT_STOPPED_BY_ADMIN, "상품 판매가 중지됐어요",
            "관리자가 운영 정책에 따라 %s 상품의 판매를 중지했어요.".formatted(command.name()), link,
            "product.changed:" + command.eventId() + ":stopped-by-admin"));
        if (exists(command.eventId())) {
            return;
        }
        memberContactService.findEmail(command.sellerId()).ifPresent(email -> {
            var content = ProductStoppedByAdminEmail.compose(notificationProperties.siteUrl(), command.productId(),
                command.name());
            createEmail(new EmailNotificationInfo(command.eventId(), command.sellerId(),
                NotificationType.PRODUCT_STOPPED_BY_ADMIN, email, content.title(), content.body()));
        });
    }

    /**
     * 알림함 알림과 같은 제목 · 본문 · 경로로 회원 웹 푸시를 만든다. 알림함 중복 키로 만든 고정 키로 한 건만 만든다.
     * 자녀 계정 푸시는 보호자 동의 정책이 정해지지 않아 보내지 않는다.
     */
    @Transactional
    public void createPush(InboxNotificationInfo inbox) {
        if (inbox.recipient().type() != InboxRecipientType.MEMBER) {
            throw new IllegalArgumentException("푸시는 회원에게만 보냅니다: " + inbox.recipient().type());
        }
        var key = pushKey(inbox.dedupKey());
        if (notificationRepository.existsByEventIdAndChannel(key, NotificationChannel.PUSH)) {
            return;
        }
        var notification = notificationRepository.save(Notification.push(new PushNotificationInfo(key,
            inbox.recipient().id(), inbox.type(), inbox.title(), inbox.body(), inbox.linkPath())));
        dispatchAfterCommit(notification.getId());
    }

    /**
     * 보존 기간(NotificationPolicy.HISTORY_RETENTION)이 지난 끝난 발송 기록을 지운다. 아직 보낼 것은 남긴다. 지운 개수.
     */
    @Transactional
    public int deleteExpired() {
        return notificationRepository.deleteFinishedCreatedBefore(
            LocalDateTime.now().minus(NotificationPolicy.HISTORY_RETENTION));
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

    // 알림함 중복 키 하나에 하나로 정해지는 키. 이메일 키(이벤트 ID · 정산서 키)와 채널이 달라 겹쳐도 상관없다
    static UUID pushKey(String inboxDedupKey) {
        return UUID.nameUUIDFromBytes(("push:" + inboxDedupKey).getBytes(UTF_8));
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
