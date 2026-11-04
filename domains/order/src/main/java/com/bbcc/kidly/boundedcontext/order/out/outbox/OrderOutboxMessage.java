package com.bbcc.kidly.boundedcontext.order.out.outbox;

import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
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
 * Kafka로 보낼 주문 이벤트(#540). 업무 개념이 아닌 전달 장치라 out에 둔다(#642). 이벤트를 발행하는 업무 트랜잭션 안에서 저장해, 업무가 롤백되면 메시지도 남지 않는다.
 * 릴레이가 전송 가능한 메시지를 id 순서대로 보내고 보낸 시각을 기록한다. 영구 실패는 격리해 원본을 보존한다. 보낸 메시지는 일정 기간 뒤 지운다.
 * 인덱스(안 보낸 메시지 · 보낸 시각)는 부분 인덱스라 마이그레이션에만 둔다.
 */
@Entity
@Table(schema = "orders", name = "order_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxMessage extends OrderTimeEntity {

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

    private LocalDateTime quarantinedAt;

    private LocalDateTime lastRetriedAt;

    private Long lastRetriedBy;

    @Column(nullable = false)
    private int retryCount;

    /**
     * 보내지 않은 메시지(시도 0)를 만든다.
     *
     * @param eventType 헤더 eventType에 넣을 값(예: order.created)
     * @param messageKey Kafka 메시지 키(이벤트의 correlationId)
     * @param payload 보낼 본문 JSON. 보낼 때 그대로 쓴다
     */
    public static OrderOutboxMessage create(UUID eventId, String eventType, String topic, String messageKey,
        String payload) {
        OrderOutboxMessage message = new OrderOutboxMessage();
        message.eventId = eventId;
        message.eventType = eventType;
        message.topic = topic;
        message.messageKey = messageKey;
        message.payload = payload;
        return message;
    }

    /**
     * 보냈다. 시도를 1 늘리고 보낸 시각을 기록한다. 이미 보낸 메시지는 바꾸지 않는다(같은 메시지를 두 번 보내도 한 번만 처리).
     */
    public void markSent(LocalDateTime now) {
        if (sentAt != null) {
            return;
        }
        if (quarantinedAt != null) {
            throw new IllegalStateException("격리 중인 아웃박스 메시지는 보냄으로 바꿀 수 없습니다.");
        }
        this.attemptCount++;
        this.sentAt = now;
    }

    /**
     * 보내지 못했다. 시도를 1 늘리고 마지막 오류를 바꾼다(칸 길이를 넘으면 앞부분만). 본문은 오류에 넣지 않는다(호출하는 쪽 책임).
     */
    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error.length() > LAST_ERROR_LENGTH ? error.substring(0, LAST_ERROR_LENGTH) : error;
    }
    /**
     * 확실한 메시지 영구 실패만 격리한다. 본문 · 이벤트 ID는 바꾸지 않고 같은 키의 후속 전송을 막는다.
     */
    public void quarantine(String error, LocalDateTime now) {
        if (sentAt != null || quarantinedAt != null) {
            throw new IllegalStateException("보냈거나 이미 격리된 아웃박스 메시지입니다.");
        }
        recordFailure(error);
        quarantinedAt = now;
    }

    /**
     * 원인을 해결한 관리자가 같은 이벤트를 다시 전송 대기시킨다. 두 번 눌러도 기록은 한 번만 바뀐다.
     */
    public void retryQuarantined(Long adminId, LocalDateTime now) {
        if (quarantinedAt == null) {
            return;
        }
        quarantinedAt = null;
        lastRetriedBy = adminId;
        lastRetriedAt = now;
        retryCount++;
    }
}
