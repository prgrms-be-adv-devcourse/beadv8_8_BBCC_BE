package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationRepository;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    private void add(String dedupKey, LocalDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status -> inboxNotificationRepository.saveIfAbsent(
            InboxNotification.create(
                new InboxNotificationInfo(1L, NotificationType.WELCOME, "제목", "본문", null, dedupKey)),
            createdAt));
    }
}
