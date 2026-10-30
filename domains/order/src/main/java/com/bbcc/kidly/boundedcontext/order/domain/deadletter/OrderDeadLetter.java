package com.bbcc.kidly.boundedcontext.order.domain.deadletter;

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
 * OPEN이면 관리자가 재처리(REPROCESSED)하거나 확인 후 종료(CLOSED)한다.
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

    /**
     * 왜 DLT로 갔는지. 관리자 권장 조치가 유형마다 다르다.
     */
    public enum FailureType {
        /** 읽기 실패: 보낸 도메인의 메시지 형식 · 값이 계약과 다름(모르는 enum 값 · 깨진 본문 · eventType 헤더 없음) */
        READ_FAILED,
        /** 보관 실패: DB · 트랜잭션 오류로 처리도 주문 자체 보관도 못 함 */
        STORE_FAILED,
        /** 그 밖의 처리 실패: 업무 규칙 · 상태 등 다시 해도 같은 결과인 예외 */
        PROCESS_FAILED
    }

    public enum Status {
        OPEN,
        REPROCESSED,
        CLOSED
    }

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
}
