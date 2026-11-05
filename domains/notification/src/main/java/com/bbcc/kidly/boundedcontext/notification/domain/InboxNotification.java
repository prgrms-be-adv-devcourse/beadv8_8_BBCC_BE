package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 알림함의 알림 한 건. 회원이 사이트에서 보고 읽음 처리한다.
 * 제목 · 본문은 넣을 때 완성해 둔다. 같은 dedupKey는 한 건만 둔다(유니크).
 */
@Entity
@Table(schema = "notification", name = "inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxNotification extends BaseTimeEntity {

    private static final int BODY_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Column(length = 500)
    private String linkPath;

    @Column(nullable = false, length = 200)
    private String dedupKey;

    private LocalDateTime readAt;

    public static InboxNotification create(InboxNotificationInfo info) {
        Assert.notNull(info.memberId(), "알림함 알림의 회원 ID는 비어 있을 수 없습니다");
        Assert.notNull(info.type(), "알림함 알림의 유형은 비어 있을 수 없습니다");
        Assert.hasText(info.title(), "알림함 알림의 제목은 비어 있을 수 없습니다");
        Assert.hasText(info.body(), "알림함 알림의 본문은 비어 있을 수 없습니다");
        Assert.hasText(info.dedupKey(), "알림함 알림의 중복 키는 비어 있을 수 없습니다");
        var notification = new InboxNotification();
        notification.memberId = info.memberId();
        notification.type = info.type();
        notification.title = info.title();
        notification.body = info.body();
        notification.linkPath = info.linkPath();
        notification.dedupKey = info.dedupKey();
        return notification;
    }

    // 읽음으로 바꾼다. 이미 읽었으면 처음 읽은 시각을 그대로 둔다
    public void read(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
