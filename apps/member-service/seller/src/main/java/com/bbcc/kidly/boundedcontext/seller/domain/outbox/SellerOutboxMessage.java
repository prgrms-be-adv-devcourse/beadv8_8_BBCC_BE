package com.bbcc.kidly.boundedcontext.seller.domain.outbox;

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

/**
 * 업무가 롤백되면 메시지도 남지 않고, 아직 보내지 않은 메시지는 sentAt이 비어 있다.
 */
@Entity
@Table(schema = "seller", name = "seller_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerOutboxMessage extends BaseTimeEntity {

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

    public static SellerOutboxMessage create(CreateInfo info) {
        var message = new SellerOutboxMessage();
        message.eventId = info.eventId();
        message.eventType = info.eventType();
        message.topic = info.topic();
        message.messageKey = info.messageKey();
        message.payload = info.payload();
        return message;
    }

    public void markSent(LocalDateTime now) {
        this.attemptCount++;
        this.sentAt = now;
    }

    /**
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
