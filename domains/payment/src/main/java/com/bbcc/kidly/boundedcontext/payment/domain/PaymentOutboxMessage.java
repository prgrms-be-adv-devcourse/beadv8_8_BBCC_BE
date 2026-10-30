package com.bbcc.kidly.boundedcontext.payment.domain;

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
 * Kafka로 보낼 결제 이벤트(#550). 이벤트를 발행한 업무 트랜잭션 안에서 저장해, 업무가 롤백되면 메시지도 남지 않는다.
 * 릴레이가 id 순서대로 아직 보내지 않은 메시지를 보내고 보낸 시각을 기록한다. 보낸 메시지는 보관 기간 뒤 지운다.
 * 인덱스(안 보낸 메시지 · 보낸 시각)는 부분 인덱스라 마이그레이션에만 둔다.
 */
@Entity
@Table(schema = "payment", name = "payment_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutboxMessage extends BaseTimeEntity {

    private static final int LAST_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 100)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private int attemptCount;

    @Column(length = LAST_ERROR_LENGTH)
    private String lastError;

    private LocalDateTime sentAt;

    /**
     * 보내지 않은 메시지(시도 0)를 만든다.
     *
     * @param eventType 헤더 eventType에 넣을 값(예: payment.completed)
     * @param messageKey Kafka 메시지 키(이벤트의 correlationId)
     * @param payload 보낼 본문 JSON. 보낼 때 그대로 쓴다
     */
    public static PaymentOutboxMessage create(UUID eventId, String eventType, String topic, String messageKey,
        String payload) {
        PaymentOutboxMessage message = new PaymentOutboxMessage();
        message.eventId = eventId;
        message.eventType = eventType;
        message.topic = topic;
        message.messageKey = messageKey;
        message.payload = payload;
        return message;
    }

    /**
     * 보냈다. 시도를 1 늘리고 보낸 시각을 기록한다. 이미 보낸 메시지는 바꾸지 않는다.
     */
    public void markSent(LocalDateTime now) {
        if (sentAt != null) {
            return;
        }
        this.attemptCount++;
        this.sentAt = now;
    }

    /**
     * 보내지 못했다. 시도를 1 늘리고 마지막 오류를 바꾼다(칸 길이를 넘으면 앞부분만). 본문은 오류에 넣지 않는다(부르는 쪽 책임).
     */
    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error.length() > LAST_ERROR_LENGTH ? error.substring(0, LAST_ERROR_LENGTH) : error;
    }
}
