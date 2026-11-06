package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationProperties;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
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

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationDispatcher,
            new NotificationProperties("https://kidly.kro.kr"), memberContactService);
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

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.ORDER_PAID);
        assertThat(saved.getValue().getRecipient()).isEqualTo("parent@kidly.co.kr");
        assertThat(saved.getValue().getBody())
            .contains("- 곰돌이 티셔츠 (네이비 / 110) × 1: 10,000원")
            .contains("- 공룡 운동화 × 1: 15,000원")
            .contains("총 결제 금액: 25,000원")
            .contains("https://kidly.kro.kr/orders/3");
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

    private void givenSaved() {
        given(notificationRepository.save(any())).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 10L);
            return notification;
        });
    }
}
