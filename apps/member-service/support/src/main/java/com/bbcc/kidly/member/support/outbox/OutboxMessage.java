package com.bbcc.kidly.member.support.outbox;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka로 보낼 메시지 한 건. 도메인 모듈의 엔티티가 상속해 자기 스키마의 표를 지정한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OutboxMessage extends BaseTimeEntity {

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

    protected OutboxMessage(CreateInfo info) {
        this.eventId = info.eventId();
        this.eventType = info.eventType();
        this.topic = info.topic();
        this.messageKey = info.messageKey();
        this.payload = info.payload();
    }

    public void markSent(LocalDateTime now) {
        this.attemptCount++;
        this.sentAt = now;
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error.substring(0, Math.min(error.length(), LAST_ERROR_LENGTH));
    }
}
