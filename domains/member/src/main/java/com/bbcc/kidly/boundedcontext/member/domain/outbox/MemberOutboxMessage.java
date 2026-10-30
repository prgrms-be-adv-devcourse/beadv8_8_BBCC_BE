package com.bbcc.kidly.boundedcontext.member.domain.outbox;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka로 보낼 회원 탈퇴 이벤트를 업무 트랜잭션 안에서 먼저 저장해 두는 아웃박스 메시지다.
 * 업무가 롤백되면 메시지도 남지 않고, 아직 보내지 않은 메시지는 sentAt이 비어 있다.
 */
@Entity
@Table(schema = "member", name = "member_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOutboxMessage extends BaseTimeEntity {

    private static final int LAST_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = LAST_ERROR_LENGTH)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public record CreateInfo(UUID eventId, String eventType, String topic, String messageKey, String payload) {
    }

    /**
     * 아직 보내지 않은 메시지(시도 0회)를 만든다.
     */
    public static MemberOutboxMessage create(CreateInfo info) {
        var message = new MemberOutboxMessage();
        message.eventId = info.eventId();
        message.eventType = info.eventType();
        message.topic = info.topic();
        message.messageKey = info.messageKey();
        message.payload = info.payload();
        return message;
    }

    /**
     * Kafka로 보냈다. 시도 횟수를 1 늘리고 보낸 시각을 기록한다.
     */
    public void markSent(LocalDateTime now) {
        this.attemptCount++;
        this.sentAt = now;
    }

    /**
     * Kafka로 보내지 못했다. 시도 횟수를 1 늘리고 마지막 오류를 바꾼다. 보낸 시각은 그대로 비워 다음 릴레이가 다시 보낸다.
     * 오류가 칸 길이보다 길면 앞부분만 남긴다. 길이를 넘기면 이 기록을 저장하는 UPDATE가 실패하기 때문이다.
     */
    public void recordFailure(String error) {
        this.attemptCount++;
        if (error.length() > LAST_ERROR_LENGTH) {
            this.lastError = error.substring(0, LAST_ERROR_LENGTH);
            return;
        }
        this.lastError = error;
    }
}
