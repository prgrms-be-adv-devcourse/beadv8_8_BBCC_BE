package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.DeliveryInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDispatcher;
import com.bbcc.kidly.boundedcontext.notification.app.PushTokenService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.DeliveryCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationChannel;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import com.bbcc.kidly.support.AsyncWait;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 웹 푸시 발송: 회원의 모든 FID로 보내기 · 결과 기록 · 무효 FID 정리. FCM 대신 FakeNotificationPushSender를 쓴다.
 */
@IntegrationTest
class PushDispatchIntegrationTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushTokenService pushTokenService;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private DeliveryInboxService deliveryInboxService;

    @Autowired
    private FakeNotificationPushSender fakeNotificationPushSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        fakeNotificationPushSender.reset();
    }

    @Test
    @DisplayName("회원의 모든 FID에 한 번에 보내고, 하나라도 받으면 SENT, 구독이 끊긴 FID는 지운다")
    void sendToAllFids() {
        pushTokenService.register(MEMBER_ID, "fid-a");
        pushTokenService.register(MEMBER_ID, FakeNotificationPushSender.UNREGISTERED + "b");
        pushTokenService.register(2L, "fid-other");
        Long id = save("/orders/3");

        notificationDispatcher.dispatch(id);

        assertThat(find(id).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(fakeNotificationPushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.fids()).containsExactlyInAnyOrder("fid-a", FakeNotificationPushSender.UNREGISTERED + "b");
            assertThat(push.title()).isEqualTo("주문하신 상품이 출발했어요");
            assertThat(push.link()).isEqualTo("http://localhost:5173/orders/3");
        });
        assertThat(pushTokenRepository.findFidsOf(MEMBER_ID)).containsExactly("fid-a");
    }

    @Test
    @DisplayName("FID가 없으면 보내지 않고 SKIPPED로 남긴다")
    void skipWithoutFid() {
        Long id = save(null);

        notificationDispatcher.dispatch(id);

        assertThat(find(id).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(fakeNotificationPushSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("경로가 없는 푸시는 누르면 사이트 첫 화면을 연다")
    void linkToHomeWithoutPath() {
        pushTokenService.register(MEMBER_ID, "fid-a");
        Long id = save(null);

        notificationDispatcher.dispatch(id);

        assertThat(fakeNotificationPushSender.sent()).singleElement()
            .satisfies(push -> assertThat(push.link()).isEqualTo("http://localhost:5173/"));
    }

    @Test
    @DisplayName("일시 오류로 아무 데도 못 보내면 FAILED와 다음 시도 시각을 남기고 FID는 지우지 않는다")
    void retryableFailure() {
        pushTokenService.register(MEMBER_ID, FakeNotificationPushSender.BUSY + "a");
        Long id = save("/orders/3");

        notificationDispatcher.dispatch(id);

        Notification notification = find(id);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNotNull();
        assertThat(notification.getLastError()).isEqualTo("FCM: [RETRYABLE]");
        assertThat(pushTokenRepository.findFidsOf(MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("모든 FID가 INVALID_ARGUMENT면 지우지 않고 더 시도하지 않는 실패로 남긴다")
    void allInvalidArgument() {
        pushTokenService.register(MEMBER_ID, FakeNotificationPushSender.INVALID + "a");
        pushTokenService.register(MEMBER_ID, FakeNotificationPushSender.INVALID + "b");
        Long id = save("/orders/3");

        notificationDispatcher.dispatch(id);

        Notification notification = find(id);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(pushTokenRepository.findFidsOf(MEMBER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("배송이 출발하면 알림함과 같은 내용의 푸시를 만들어 커밋 뒤 보내고, 같은 배송은 한 건이다")
    void deliveryShippedPush() {
        pushTokenService.register(MEMBER_ID, "fid-a");
        var command = new DeliveryCommand(7L, 3L, MEMBER_ID, "CJ대한통운", "123456789012");

        deliveryInboxService.addShipped(command);
        deliveryInboxService.addShipped(command);

        AsyncWait.until(() -> !fakeNotificationPushSender.sent().isEmpty());
        assertThat(fakeNotificationPushSender.sent()).singleElement().satisfies(push -> {
            assertThat(push.body()).isEqualTo("CJ대한통운 운송장 123456789012로 출발했어요.");
            assertThat(push.link()).isEqualTo("http://localhost:5173/orders/3");
        });
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notification.notification WHERE channel = ?", Long.class,
            NotificationChannel.PUSH.name())).isEqualTo(1L);
        AsyncWait.until(() -> jdbcTemplate.queryForObject(
            "SELECT status FROM notification.notification WHERE channel = 'PUSH'", String.class).equals("SENT"));
    }

    private Long save(String linkPath) {
        return notificationRepository.save(NotificationFixture.deliveryPush(MEMBER_ID, linkPath)).getId();
    }

    private Notification find(Long id) {
        return notificationRepository.findById(id).orElseThrow();
    }
}
