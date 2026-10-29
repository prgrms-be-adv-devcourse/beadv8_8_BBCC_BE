package com.bbcc.kidly.boundedcontext.product.domain.outbox;

import static java.util.Objects.requireNonNull;

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
 * Kafka로 보낼 상품 이벤트를 업무 트랜잭션 안에서 먼저 저장해 두는 아웃박스 메시지다.
 * 업무가 롤백되면 메시지도 남지 않고, 아직 보내지 않은 메시지는 sentAt이 비어 있다.
 */
@Entity
@Table(schema = "product", name = "product_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOutboxMessage extends BaseTimeEntity {

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
    public static ProductOutboxMessage create(CreateInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ProductOutboxMessage message = new ProductOutboxMessage();
        message.eventId = requireNonNull(info.eventId(), "eventId는 필수입니다");
        message.eventType = requireNonNull(info.eventType(), "eventType은 필수입니다");
        message.topic = requireNonNull(info.topic(), "topic은 필수입니다");
        message.messageKey = requireNonNull(info.messageKey(), "messageKey는 필수입니다");
        message.payload = requireNonNull(info.payload(), "payload는 필수입니다");
        return message;
    }

    /**
     * Kafka로 보냈다. 시도 횟수를 1 늘리고 보낸 시각을 기록한다.
     * 이미 보낸 메시지는 바꾸지 않으므로, 같은 객체에 두 번 불러도 처음 기록이 남는다.
     */
    public void markSent(LocalDateTime now) {
        requireNonNull(now, "now는 필수입니다");
        if (sentAt != null) {
            return;
        }
        this.attemptCount++;
        this.sentAt = now;
    }

    /**
     * Kafka로 보내지 못했다. 시도 횟수를 1 늘리고 마지막 오류를 바꾼다. 보낸 시각은 그대로 비워 다음 릴레이가 다시 보낸다.
     * 오류가 칸 길이보다 길면 앞부분만 남긴다. 길이를 넘기면 이 기록을 저장하는 UPDATE가 실패하기 때문이다.
     */
    public void recordFailure(String error) {
        requireNonNull(error, "error는 필수입니다");
        this.attemptCount++;
        if (error.length() > LAST_ERROR_LENGTH) {
            this.lastError = error.substring(0, LAST_ERROR_LENGTH);
            return;
        }
        this.lastError = error;
    }
}
