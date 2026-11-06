package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class InboxNotificationRepositoryIntegrationTest {

    @Autowired
    private InboxNotificationRepository inboxNotificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기준 시각보다 먼저 만든 알림만 읽음 여부와 상관없이 지운다")
    void deleteCreatedBefore() {
        var cutoff = LocalDateTime.of(2026, 8, 1, 4, 0);
        add("old-unread", cutoff.minusSeconds(1));
        add("old-read", cutoff.minusDays(10));
        jdbcTemplate.update("UPDATE notification.inbox SET read_at = ? WHERE dedup_key = 'old-read'", cutoff);
        add("at-cutoff", cutoff);
        add("new", cutoff.plusDays(1));

        var deleted = transactionTemplate.execute(status -> inboxNotificationRepository.deleteCreatedBefore(cutoff));

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT dedup_key FROM notification.inbox ORDER BY id", String.class))
            .isEqualTo(List.of("at-cutoff", "new"));
    }

    @Test
    @DisplayName("같은 숫자 ID라도 받는 사람 종류가 다르면 조회 · 개수 · 모두 읽음 · 삭제에 섞이지 않는다")
    void filterByRecipientTypeAndId() {
        var member = InboxRecipient.member(7L);
        var child = InboxRecipient.child(7L);
        add(member, "member", LocalDateTime.now());
        add(child, "child", LocalDateTime.now());
        var memberNotificationId = idOf("member");

        assertThat(inboxNotificationRepository.findNewestFirst(child, PageRequest.of(0, 20)))
            .extracting(InboxNotification::getDedupKey).containsExactly("child");
        assertThat(inboxNotificationRepository.findByIdAndRecipient(memberNotificationId, child)).isEmpty();
        assertThat(inboxNotificationRepository.countUnread(child)).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(inboxNotificationRepository.readAllUnread(child, LocalDateTime.now())).isEqualTo(1);
            assertThat(inboxNotificationRepository.deleteAllOf(child)).isEqualTo(1);
        });

        assertThat(inboxNotificationRepository.countUnread(member)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT dedup_key FROM notification.inbox", String.class))
            .containsExactly("member");
    }

    @Test
    @DisplayName("받는 사람 종류는 MEMBER · CHILD만 저장된다")
    void rejectUnknownRecipientType() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO notification.inbox
                (recipient_type, recipient_id, type, title, body, dedup_key, created_at, updated_at)
            VALUES ('ADMIN', 1, 'WELCOME', '제목', '본문', 'k', now(), now())
            """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void add(String dedupKey, LocalDateTime createdAt) {
        add(InboxRecipient.member(1L), dedupKey, createdAt);
    }

    private Long idOf(String dedupKey) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM notification.inbox WHERE dedup_key = ?", Long.class, dedupKey);
    }

    private void add(InboxRecipient recipient, String dedupKey, LocalDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status -> inboxNotificationRepository.saveIfAbsent(
            InboxNotification.create(
                new InboxNotificationInfo(recipient, NotificationType.WELCOME, "제목", "본문", null, dedupKey)),
            createdAt));
    }
}
