package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Handling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Status;
import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 DLT로 간 메시지(#601). 재시도해도 계속 실패해 사람이 확인해야 하는 메시지를 원래 위치(토픽 · 파티션 · 오프셋)당 1건 남긴다.
 * 처음 저장 · 다시 받음은 저장소의 한 문장(upsert)이 한다: 다시 받으면 받은 횟수 · 마지막 받은 시각만 바뀌고 처음 값 · 상태는 그대로다.
 * OPEN이면 관리자가 재처리(REPROCESSED)하거나 확인 후 종료(CLOSED)한다. Kafka 실패 처리 장치라 out에 두고, 관리자 처리 규칙은
 * app(DeadLetterHandling)이 정해 처리 칸만 되돌려 쓴다(#642).
 */
@Entity
@Table(schema = "orders", name = "order_dead_letter",
    uniqueConstraints = @UniqueConstraint(name = OrderDeadLetter.SOURCE_UNIQUE,
        columnNames = {"source_topic", "source_partition", "source_offset"}),
    indexes = @Index(name = "idx_order_dead_letter_status", columnList = "status, last_received_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeadLetter extends OrderTimeEntity {

    public static final String SOURCE_UNIQUE = "uk_order_dead_letter_source";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sourceTopic;

    @Column(nullable = false)
    private int sourcePartition;

    @Column(nullable = false)
    private long sourceOffset;

    @Column(length = 100)
    private String messageKey;

    @Column(length = 60)
    private String eventType;

    @Column(length = 100)
    private String eventId;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(length = 30)
    private String producer;

    private LocalDateTime occurredAt;

    @Column(length = 100)
    private String aggregateId;

    @Column(length = 100)
    private String correlationId;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailureType failureType;

    @Column(length = 300)
    private String exceptionClass;

    @Column(length = 1000)
    private String exceptionMessage;

    @Column(columnDefinition = "text")
    private String stackTraceHead;

    @Column(nullable = false)
    private LocalDateTime firstReceivedAt;

    @Column(nullable = false)
    private LocalDateTime lastReceivedAt;

    @Column(nullable = false)
    private int receivedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private Long handledBy;

    private LocalDateTime handledAt;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false)
    private int reprocessCount;

    private LocalDateTime lastReprocessedAt;

    @Column(length = 500)
    private String lastReprocessResult;

    @Version
    private Long version;

    /**
     * 관리자 처리 결과를 반영한다(받은 값 칸은 바꾸지 않음). 처리 규칙은 app이 정한다.
     */
    public void applyHandling(Handling handling) {
        this.status = handling.status();
        this.handledBy = handling.handledBy();
        this.handledAt = handling.handledAt();
        this.memo = handling.memo();
        this.reprocessCount = handling.reprocessCount();
        this.lastReprocessedAt = handling.lastReprocessedAt();
        this.lastReprocessResult = handling.lastReprocessResult();
    }

    public Handling getHandling() {
        return new Handling(status, handledBy, handledAt, memo, reprocessCount, lastReprocessedAt, lastReprocessResult);
    }
}
