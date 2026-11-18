package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
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

@Entity
@Table(schema = "auth", name = "auth_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthOutboxMessage extends BaseTimeEntity {
    private static final int LAST_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private UUID eventId;
    @Column(nullable = false, length = 60)
    private String eventType;
    @Column(nullable = false, length = 100)
    private String messageKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false)
    private int attemptCount;
    @Column(length = LAST_ERROR_LENGTH)
    private String lastError;
    private LocalDateTime sentAt;

    public static AuthOutboxMessage create(UUID eventId, String eventType, String messageKey, String payload) {
        var message = new AuthOutboxMessage();
        message.eventId = eventId;
        message.eventType = eventType;
        message.messageKey = messageKey;
        message.payload = payload;
        return message;
    }

    public void markSent(LocalDateTime now) {
        this.attemptCount++;
        this.sentAt = now;
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        if (error.length() > LAST_ERROR_LENGTH) {
            this.lastError = error.substring(0, LAST_ERROR_LENGTH);
            return;
        }
        this.lastError = error;
    }
}
