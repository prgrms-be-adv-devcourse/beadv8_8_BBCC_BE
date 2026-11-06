package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.app.InboxNotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.MemberWithdrawalService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationSendService;
import com.bbcc.kidly.boundedcontext.notification.domain.EmailNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationStatus;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.boundedcontext.notification.out.JpaMemberContactRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberWithdrawalServiceIntegrationTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
    private static final String EMAIL = "parent@kidly.co.kr";
    private static final String OTHER_EMAIL = "other@kidly.co.kr";

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

    @Autowired
    private InboxNotificationService inboxNotificationService;

    @Autowired
    private MemberContactService memberContactService;

    @Autowired
    private NotificationSendService notificationSendService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JpaMemberContactRepository jpaMemberContactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴하면 그 회원의 알림함 · 이메일 복사본 · 푸시 FID를 지우고, 같은 숫자 ID의 자녀 알림과 다른 회원 것은 남긴다")
    void deleteInboxAndContact() {
        addInbox(InboxRecipient.member(MEMBER_ID), "member");
        addInbox(InboxRecipient.child(MEMBER_ID), "child");
        addInbox(InboxRecipient.member(OTHER_MEMBER_ID), "other");
        memberContactService.save(MEMBER_ID, EMAIL);
        memberContactService.save(OTHER_MEMBER_ID, OTHER_EMAIL);
        addPushFid(MEMBER_ID, "fid-member");
        addPushFid(OTHER_MEMBER_ID, "fid-other");

        memberWithdrawalService.purge(MEMBER_ID);

        assertThat(jdbcTemplate.queryForList("SELECT dedup_key FROM notification.inbox ORDER BY id", String.class))
            .containsExactly("child", "other");
        assertThat(jpaMemberContactRepository.existsById(MEMBER_ID)).isFalse();
        assertThat(jpaMemberContactRepository.existsById(OTHER_MEMBER_ID)).isTrue();
        assertThat(jdbcTemplate.queryForList("SELECT fid FROM notification.push_token", String.class))
            .containsExactly("fid-other");
    }

    private void addPushFid(Long memberId, String fid) {
        jdbcTemplate.update("""
            INSERT INTO notification.push_token (member_id, fid, last_registered_at, created_at, updated_at)
            VALUES (?, ?, now(), now(), now())
            """, memberId, fid);
    }

    @Test
    @DisplayName("탈퇴하면 발송 기록의 주소를 비우고 보내지 않은 것만 SKIPPED로 끝낸다. 두 번 받아도 같다")
    void eraseEmailRecords() {
        var now = LocalDateTime.now();
        var pending = save(email(MEMBER_ID, EMAIL));
        var retryable = save(failed(email(MEMBER_ID, EMAIL), true, now));
        var givenUp = save(failed(email(MEMBER_ID, EMAIL), false, now));
        var sending = save(sending(email(MEMBER_ID, EMAIL), now));
        var sent = save(sent(email(MEMBER_ID, EMAIL), now));
        var other = save(email(OTHER_MEMBER_ID, OTHER_EMAIL));

        memberWithdrawalService.purge(MEMBER_ID);
        memberWithdrawalService.purge(MEMBER_ID);

        assertThat(find(pending).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(find(retryable)).satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
            assertThat(notification.getNextAttemptAt()).isNull();
            assertThat(notification.getAttemptCount()).isEqualTo(1);
        });
        assertThat(find(givenUp).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(find(sending).getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(find(sent)).satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
            assertThat(notification.getType()).isEqualTo(NotificationType.WELCOME);
        });
        assertThat(jdbcTemplate.queryForList(
            "SELECT recipient FROM notification.notification WHERE member_id = ?", String.class, MEMBER_ID))
            .hasSize(5).containsOnlyNulls();
        assertThat(find(other)).satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getRecipient()).isEqualTo(OTHER_EMAIL);
        });
    }

    @Test
    @DisplayName("탈퇴 전에 발송을 맡으려고 읽은 알림은 저장할 때 버전 충돌로 물러난다")
    void conflictWithClaimReadBeforePurge() {
        var id = save(email(MEMBER_ID, EMAIL));
        var readBeforePurge = notificationRepository.findById(id).orElseThrow();

        memberWithdrawalService.purge(MEMBER_ID);
        readBeforePurge.startSending(LocalDateTime.now());

        assertThatThrownBy(() -> notificationRepository.save(readBeforePurge))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("발송 중에 탈퇴해 멈춘 알림을 다시 맡으면 주소가 없어 보내지 않고 SKIPPED로 끝낸다")
    void skipStaleSendingWithoutRecipient() {
        var id = save(sending(email(MEMBER_ID, EMAIL), LocalDateTime.now().minusMinutes(10)));

        memberWithdrawalService.purge(MEMBER_ID);

        assertThat(notificationSendService.start(id)).isEmpty();
        assertThat(find(id).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    private void addInbox(InboxRecipient recipient, String dedupKey) {
        inboxNotificationService.add(
            new InboxNotificationInfo(recipient, NotificationType.WELCOME, "제목", "본문", null, dedupKey));
    }

    private static Notification email(Long memberId, String recipient) {
        return Notification.email(new EmailNotificationInfo(
            UUID.randomUUID(), memberId, NotificationType.WELCOME, recipient, "제목", "본문"));
    }

    private static Notification sending(Notification notification, LocalDateTime startedAt) {
        notification.startSending(startedAt);
        return notification;
    }

    private static Notification failed(Notification notification, boolean retryable, LocalDateTime now) {
        sending(notification, now).markFailed("연결 실패", retryable, now);
        return notification;
    }

    private static Notification sent(Notification notification, LocalDateTime now) {
        sending(notification, now).markSent(now);
        return notification;
    }

    private Long save(Notification notification) {
        return notificationRepository.save(notification).getId();
    }

    private Notification find(Long id) {
        return notificationRepository.findById(id).orElseThrow();
    }
}
