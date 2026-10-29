package com.bbcc.kidly.boundedcontext.product.domain.outbox;

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

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public record CreateInfo(UUID eventId, String eventType, String topic, String messageKey, String payload) {
    }

    /**
     * 아직 보내지 않은 메시지(시도 0회)를 만든다.
     */
    public static ProductOutboxMessage create(CreateInfo info) {
        ProductOutboxMessage message = new ProductOutboxMessage();
        message.eventId = info.eventId();
        message.eventType = info.eventType();
        message.topic = info.topic();
        message.messageKey = info.messageKey();
        message.payload = info.payload();
        return message;
    }
}
