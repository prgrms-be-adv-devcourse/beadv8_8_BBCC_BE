package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotification;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InboxNotificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    @Test
    @DisplayName("처음 만든 알림은 안 읽음이다")
    void createUnread() {
        var notification = InboxNotification.create(info("제목"));

        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("읽으면 읽은 시각을 남기고, 다시 읽어도 처음 시각을 그대로 둔다")
    void readTwice() {
        var notification = InboxNotification.create(info("제목"));

        notification.read(NOW);
        notification.read(NOW.plusMinutes(5));

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("제목이 비어 있으면 만들 수 없다")
    void blankTitle(String title) {
        assertThatThrownBy(() -> InboxNotification.create(info(title)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static InboxNotificationInfo info(String title) {
        return new InboxNotificationInfo(1L, NotificationType.WELCOME, title, "본문", null, "key-1");
    }
}
