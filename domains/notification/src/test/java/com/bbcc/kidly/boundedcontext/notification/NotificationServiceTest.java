package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationProperties;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final WelcomeEmailCommand COMMAND =
        new WelcomeEmailCommand(EVENT_ID, 1L, "parent@kidly.co.kr");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private MemberContactService memberContactService;

    @Mock
    private InboxNotificationService inboxNotificationService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationDispatcher,
            new NotificationProperties("https://kidly.kro.kr"), memberContactService, inboxNotificationService);
        // 서비스는 @Transactional 안에서 불리므로, 커밋 뒤 콜백을 등록할 수 있게 동기화를 켠다
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("환영 메일을 PENDING으로 저장하고, 커밋 뒤에 비동기 발송을 넘긴다")
    void createWelcomeEmail() {
        given(notificationRepository.existsByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
            .willReturn(false);
        given(notificationRepository.save(any())).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 10L);
            return notification;
        });

        notificationService.createWelcomeEmail(COMMAND);

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getValue().getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(saved.getValue().getBody()).contains("https://kidly.kro.kr");
        verify(notificationDispatcher, never()).dispatchAsync(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(notificationDispatcher).dispatchAsync(10L);
    }

    @Test
    @DisplayName("같은 가입 이벤트로 만든 환영 메일이 있으면 만들지 않는다")
    void duplicateEvent() {
        given(notificationRepository.existsByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
            .willReturn(true);

        notificationService.createWelcomeEmail(COMMAND);

        verify(notificationRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    @DisplayName("정산금 지급 메일을 판매자 이메일로 만들고, 금액과 정산 상세 주소를 넣는다")
    void createPayoutEmail() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.co.kr"));
        givenSaved();

        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.PAYOUT_COMPLETED);
        assertThat(saved.getValue().getMemberId()).isEqualTo(5L);
        assertThat(saved.getValue().getRecipient()).isEqualTo("seller@kidly.co.kr");
        assertThat(saved.getValue().getBody())
            .contains("12,000원").contains("https://kidly.kro.kr/center/settlements/7");
    }

    @Test
    @DisplayName("같은 정산서의 지급 완료는 이벤트 ID가 달라도 같은 키로 만들어, 이미 있으면 다시 만들지 않는다")
    void payoutKeyBySettlement() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.co.kr"));
        givenSaved();

        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));
        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        given(notificationRepository.existsByEventIdAndChannel(saved.getValue().getEventId(),
            NotificationChannel.EMAIL)).willReturn(true);

        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("0원 정산은 입금이 없어 지급 메일을 만들지 않는다")
    void skipZeroPayout() {
        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 0L));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("받는 사람의 이메일 복사본이 없으면 지급 메일을 만들지 않는다")
    void skipPayoutWithoutContact() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.empty());

        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("환불 완료 메일을 환불받은 회원 이메일로 만들고, 금액과 주문 상세 주소를 넣는다")
    void createRefundEmail() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, 1L, 3L, 34_500L));

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.REFUND_COMPLETED);
        assertThat(saved.getValue().getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(saved.getValue().getBody()).contains("34,500원").contains("https://kidly.kro.kr/orders/3");
    }

    @Test
    @DisplayName("회원 ID가 없는 예전 환불 이벤트는 받는 사람을 알 수 없어 만들지 않는다")
    void skipRefundWithoutMember() {
        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, null, 3L, 34_500L));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("계정 정지 메일에 정지 사유를 넣어 정지된 회원 이메일로 만든다")
    void createSuspendedEmail() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createSuspendedEmail(EVENT_ID, 1L, "운영 정책 위반");

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.MEMBER_SUSPENDED);
        assertThat(saved.getValue().getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(saved.getValue().getBody()).contains("정지 사유: 운영 정책 위반");
    }

    @Test
    @DisplayName("판매자 신청 거절 메일에 거절 사유를 넣어 신청자 이메일로 만든다")
    void createApplicationRejectedEmail() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createApplicationRejectedEmail(EVENT_ID, 1L, "사업자 정보 불일치");

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.SELLER_APPLICATION_REJECTED);
        assertThat(saved.getValue().getBody()).contains("거절 사유: 사업자 정보 불일치");
    }

    @Test
    @DisplayName("판매자 승인 메일에 상호명과 판매자 센터 주소를 넣어 판매자 이메일로 만든다")
    void createSellerApprovedEmail() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.co.kr"));
        givenSaved();

        notificationService.createSellerApprovedEmail(EVENT_ID, 5L, "키들리 상점");

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.SELLER_APPROVED);
        assertThat(saved.getValue().getRecipient()).isEqualTo("seller@kidly.co.kr");
        assertThat(saved.getValue().getBody()).contains("키들리 상점").contains("https://kidly.kro.kr/center");
    }

    @Test
    @DisplayName("판매자 정지 메일에 상호명을 넣어 판매자 이메일로 만든다")
    void createSellerSuspendedEmail() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.co.kr"));
        givenSaved();

        notificationService.createSellerSuspendedEmail(EVENT_ID, 5L, "키들리 상점");

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.SELLER_SUSPENDED);
        assertThat(saved.getValue().getBody()).contains("키들리 상점");
    }

    @Test
    @DisplayName("판매자의 이메일 복사본이 없으면 승인 메일을 만들지 않는다")
    void skipSellerApprovedWithoutContact() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.empty());

        notificationService.createSellerApprovedEmail(EVENT_ID, 5L, "키들리 상점");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 확인 메일에 상품 · 옵션 · 수량 · 금액과 총액, 주문 상세 주소를 넣어 구매자 이메일로 만든다")
    void createOrderPaidEmail() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 25_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", "네이비 / 110", 1, 10_000L),
            new OrderPaidCommand.Item(5L, "공룡 운동화", null, 1, 15_000L))));

        var email = saved(NotificationChannel.EMAIL);
        assertThat(email.getType()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(email.getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(email.getBody())
            .contains("- 곰돌이 티셔츠 (네이비 / 110) × 1: 10,000원")
            .contains("- 공룡 운동화 × 1: 15,000원")
            .contains("총 결제 금액: 25,000원")
            .contains("https://kidly.kro.kr/orders/3");
    }

    @Test
    @DisplayName("주문상품이 많고 이름이 길어도 상품은 5개까지만 적고 나머지는 외 N건으로 줄여 본문 한도 안에서 만든다")
    void orderPaidEmailWithManyItems() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();
        var item = new OrderPaidCommand.Item(5L, "가".repeat(255), "나".repeat(255), 999, 9_999_999_999L);

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 9_999_999_999L,
            Collections.nCopies(30, item)));

        assertThat(saved(NotificationChannel.EMAIL).getBody()).hasSizeLessThanOrEqualTo(4000).contains("- 외 25건");
    }

    @Test
    @DisplayName("구매자의 이메일 복사본이 없으면 주문 확인 메일은 만들지 않고 푸시만 만든다")
    void skipOrderPaidWithoutContact() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.empty());
        givenSaved();

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 10_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", null, 1, 10_000L))));

        assertThat(allSaved()).extracting(Notification::getChannel).containsExactly(NotificationChannel.PUSH);
    }

    @Test
    @DisplayName("같은 주문 결제완료 이벤트를 다시 받으면 주문 확인 메일 · 푸시를 다시 만들지 않는다")
    void skipDuplicateOrderPaid() {
        given(notificationRepository.existsByEventIdAndChannel(any(), any())).willReturn(true);

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 10_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", null, 1, 10_000L))));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("상품이 빈 결제완료 메시지도 실패시키지 않고 상품 이름 없이 메일과 알림함을 만든다")
    void orderPaidWithoutItems() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 10_000L, List.of()));

        assertThat(allSaved()).extracting(Notification::getChannel)
            .containsExactly(NotificationChannel.PUSH, NotificationChannel.EMAIL);
        var added = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService).add(added.capture());
        assertThat(added.getValue().body()).isEqualTo("주문의 결제가 끝났어요. 총 결제 금액은 10,000원이에요.");
    }

    @Test
    @DisplayName("사유가 없으면 사유 줄 없이 만든다")
    void suspendedWithoutReason() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createSuspendedEmail(EVENT_ID, 1L, null);

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getBody()).doesNotContain("사유");
    }

    @Test
    @DisplayName("계정 정지를 뺀 메일은 받는 사람 알림함에도 종류별로 한 건씩 남긴다")
    void addInboxWithEmail() {
        given(memberContactService.findEmail(any())).willReturn(Optional.of("member@kidly.co.kr"));
        givenSaved();

        notificationService.createWelcomeEmail(COMMAND);
        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 25_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", "네이비 / 110", 1, 10_000L),
            new OrderPaidCommand.Item(5L, "공룡 운동화", null, 1, 15_000L))));
        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, 1L, 3L, 34_500L));
        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));
        notificationService.createSellerApprovedEmail(EVENT_ID, 5L, "키들리 상점");
        notificationService.createApplicationRejectedEmail(EVENT_ID, 1L, "사업자 정보 불일치");
        notificationService.createSellerSuspendedEmail(EVENT_ID, 5L, "키들리 상점");

        var added = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService, times(7)).add(added.capture());
        assertThat(added.getAllValues())
            .extracting(InboxNotificationInfo::recipient, InboxNotificationInfo::type, InboxNotificationInfo::linkPath,
                InboxNotificationInfo::dedupKey)
            .containsExactly(
                tuple(InboxRecipient.member(1L), NotificationType.WELCOME, null, "member.registered:" + EVENT_ID),
                tuple(InboxRecipient.member(1L), NotificationType.ORDER_PAID, "/orders/3", "order.paid:3"),
                tuple(InboxRecipient.member(1L),
                    NotificationType.REFUND_COMPLETED, "/orders/3", "payment.refunded:" + EVENT_ID),
                tuple(InboxRecipient.member(5L),
                    NotificationType.PAYOUT_COMPLETED, "/center/settlements/7", "payment.payout-completed:7"),
                tuple(InboxRecipient.member(5L),
                    NotificationType.SELLER_APPROVED, "/center", "seller.changed:" + EVENT_ID),
                tuple(InboxRecipient.member(1L), NotificationType.SELLER_APPLICATION_REJECTED, null,
                    "seller.application-rejected:" + EVENT_ID),
                tuple(InboxRecipient.member(5L),
                    NotificationType.SELLER_SUSPENDED, null, "seller.changed:" + EVENT_ID));
        assertThat(added.getAllValues()).extracting(InboxNotificationInfo::body).containsExactly(
            "자녀의 키와 체형을 등록하면 지금 잘 맞는 사이즈의 옷을 골라 드려요.",
            "곰돌이 티셔츠 외 1건 주문의 결제가 끝났어요. 총 결제 금액은 25,000원이에요.",
            "취소한 주문의 34,500원을 예치금으로 돌려드렸어요.",
            "정산금 12,000원이 예치금으로 입금됐어요.",
            "키들리 상점 상호의 판매자 신청이 승인됐어요. 판매자 센터에서 상품을 등록해 보세요.",
            "보내 주신 판매자 신청이 거절됐어요. 거절 사유: 사업자 정보 불일치",
            "관리자가 키들리 상점 상호의 판매자 자격을 정지했어요.");
    }

    @Test
    @DisplayName("계정 정지는 로그인이 막혀 알림함을 볼 수 없으므로 메일만 만들고 알림함에는 남기지 않는다")
    void skipInboxForSuspended() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.of("parent@kidly.co.kr"));
        givenSaved();

        notificationService.createSuspendedEmail(EVENT_ID, 1L, "운영 정책 위반");

        verify(notificationRepository).save(any());
        verify(inboxNotificationService, never()).add(any());
    }

    @Test
    @DisplayName("이메일 복사본이 없어 메일을 못 만들어도 알림함에는 남긴다")
    void addInboxWithoutContact() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.empty());

        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, 1L, 3L, 34_500L));

        verify(notificationRepository, never()).save(any());
        verify(inboxNotificationService).add(any());
    }

    @Test
    @DisplayName("같은 일을 다시 받으면 같은 중복 키로 넣어 알림함은 한 건이다(지급 완료는 새 이벤트 ID여도 정산서 기준)")
    void sameInboxKeyOnRedelivery() {
        given(notificationRepository.existsByEventIdAndChannel(any(), any())).willReturn(true);

        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, 1L, 3L, 34_500L));
        notificationService.createRefundEmail(new RefundEmailCommand(EVENT_ID, 1L, 3L, 34_500L));
        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));
        notificationService.createPayoutEmail(new PayoutEmailCommand(7L, 5L, 12_000L));

        var added = ArgumentCaptor.forClass(InboxNotificationInfo.class);
        verify(inboxNotificationService, times(4)).add(added.capture());
        assertThat(added.getAllValues()).extracting(InboxNotificationInfo::dedupKey).containsExactly(
            "payment.refunded:" + EVENT_ID, "payment.refunded:" + EVENT_ID,
            "payment.payout-completed:7", "payment.payout-completed:7");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("관리자 판매 중지는 판매자 이메일로 상품 이름 · 판매자 센터 상품 주소를 담은 메일을 만들고, 알림함에도 남긴다")
    void createProductStoppedByAdminEmail() {
        given(memberContactService.findEmail(5L)).willReturn(Optional.of("seller@kidly.co.kr"));
        givenSaved();

        notificationService.createProductStoppedByAdminEmail(new ProductChangedCommand(EVENT_ID, 9L, 5L, "곰돌이 티셔츠",
            ProductStatus.STOPPED, true, List.of(),
            LocalDateTime.of(2026, 11, 6, 9, 0)));

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.PRODUCT_STOPPED_BY_ADMIN);
        assertThat(saved.getValue().getRecipient()).isEqualTo("seller@kidly.co.kr");
        assertThat(saved.getValue().getBody())
            .contains("곰돌이 티셔츠", "https://kidly.kro.kr/center/products/9");
        verify(inboxNotificationService).add(new InboxNotificationInfo(InboxRecipient.member(5L),
            NotificationType.PRODUCT_STOPPED_BY_ADMIN,
            "상품 판매가 중지됐어요", "관리자가 운영 정책에 따라 곰돌이 티셔츠 상품의 판매를 중지했어요.", "/center/products/9",
            "product.changed:" + EVENT_ID + ":stopped-by-admin"));
    }

    @Test
    @DisplayName("주문 확인은 알림함과 같은 제목 · 본문 · 경로로 구매자 푸시를 만들고, 알림함 중복 키로 정한 키로 커밋 뒤 보낸다")
    void createOrderPaidPush() {
        given(memberContactService.findEmail(1L)).willReturn(Optional.empty());
        givenSaved();

        notificationService.createOrderPaidEmail(new OrderPaidCommand(EVENT_ID, 3L, 1L, 10_000L, List.of(
            new OrderPaidCommand.Item(5L, "곰돌이 티셔츠", null, 1, 10_000L))));

        var push = saved(NotificationChannel.PUSH);
        assertThat(push.getMemberId()).isEqualTo(1L);
        assertThat(push.getType()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(push.getTitle()).isEqualTo("주문이 결제됐어요");
        assertThat(push.getBody()).isEqualTo("곰돌이 티셔츠 주문의 결제가 끝났어요. 총 결제 금액은 10,000원이에요.");
        assertThat(push.getLinkPath()).isEqualTo("/orders/3");
        assertThat(push.getRecipient()).isNull();
        assertThat(push.getEventId())
            .isEqualTo(UUID.nameUUIDFromBytes("push:order.paid:3".getBytes(UTF_8)));
        verify(notificationDispatcher, never()).dispatchAsync(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(notificationDispatcher).dispatchAsync(10L);
    }

    @Test
    @DisplayName("같은 알림함 키의 푸시가 이미 있으면 다시 만들지 않는다")
    void skipDuplicatePush() {
        given(notificationRepository.existsByEventIdAndChannel(any(), any())).willReturn(true);

        notificationService.createPush(new InboxNotificationInfo(InboxRecipient.member(1L),
            NotificationType.DELIVERY_SHIPPED, "제목", "본문", "/orders/3", "delivery.shipped:7"));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("자녀 계정 알림은 푸시로 보내지 않는다")
    void rejectChildPush() {
        var childInbox = new InboxNotificationInfo(InboxRecipient.child(1L),
            NotificationType.WISH_REJECTED, "제목", "본문", null, "wish.rejected:1");

        assertThatThrownBy(() -> notificationService.createPush(childInbox))
            .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    private List<Notification> allSaved() {
        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(saved.capture());
        return saved.getAllValues();
    }

    private Notification saved(NotificationChannel channel) {
        return allSaved().stream().filter(n -> n.getChannel() == channel).findFirst().orElseThrow();
    }

    private void givenSaved() {
        given(notificationRepository.save(any())).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 10L);
            return notification;
        });
    }
}
