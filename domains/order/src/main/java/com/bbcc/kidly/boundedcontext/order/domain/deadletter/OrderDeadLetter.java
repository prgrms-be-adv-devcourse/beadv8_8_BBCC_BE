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
    public static final int RESULT_LENGTH = 500;
    public static final String HANDED_OFF_RESULT = "자체 보관으로 넘김(재처리 대기 목록에서 처리)";
    /** 결제 토픽. 여기서 온 메시지는 돈이 걸려 있어 관리자 목록 맨 앞에 둔다(#632) */
    public static final String PAYMENT_SOURCE_TOPIC = "kidly.payment.events";

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

    @Column(length = RESULT_LENGTH)
    private String lastReprocessResult;

    @Version
    private Long version;

    /**
     * 처음 받은 메시지의 행(OPEN · 받은 횟수 1). 실제 저장은 저장소의 한 문장(upsert)이 같은 값으로 한다.
     */
    public static OrderDeadLetter received(ReceivedDeadLetter letter) {
        OrderDeadLetter deadLetter = new OrderDeadLetter();
        deadLetter.sourceTopic = letter.sourceTopic();
        deadLetter.sourcePartition = letter.sourcePartition();
        deadLetter.sourceOffset = letter.sourceOffset();
        deadLetter.messageKey = letter.messageKey();
        deadLetter.eventType = letter.eventType();
        deadLetter.eventId = letter.eventId();
        deadLetter.payload = letter.payload();
        deadLetter.producer = letter.producer();
        deadLetter.occurredAt = letter.occurredAt();
        deadLetter.aggregateId = letter.aggregateId();
        deadLetter.correlationId = letter.correlationId();
        deadLetter.orderId = letter.orderId();
        deadLetter.failureType = letter.failureType();
        deadLetter.exceptionClass = letter.exceptionClass();
        deadLetter.exceptionMessage = letter.exceptionMessage();
        deadLetter.stackTraceHead = letter.stackTraceHead();
        deadLetter.firstReceivedAt = letter.receivedAt();
        deadLetter.lastReceivedAt = letter.receivedAt();
        deadLetter.receivedCount = 1;
        deadLetter.status = Status.OPEN;
        deadLetter.reprocessCount = 0;
        return deadLetter;
    }

    /**
     * 재처리로 처리를 마쳤다. 처리자 · 시각을 남기고 마지막 실패 이유는 지운다. OPEN만(서비스가 먼저 거른다).
     */
    public void markReprocessed(Long adminId, LocalDateTime now) {
        requireOpen();
        this.status = Status.REPROCESSED;
        this.handledBy = adminId;
        this.handledAt = now;
        this.reprocessCount++;
        this.lastReprocessedAt = now;
        this.lastReprocessResult = null;
    }

    /**
     * 재처리에서 원래 처리가 실패를 주문 자체 보관(결제 반영 실패 · 이벤트 처리 실패 기록)에 넘겼다. 이 메시지로 할 일은 끝나
     * REPROCESSED지만, 진짜 처리 성공과 구분되게 결과에 넘김을 표시한다(이후는 그 재처리 대기 목록에서).
     */
    public void markHandedOff(Long adminId, LocalDateTime now) {
        markReprocessed(adminId, now);
        this.lastReprocessResult = HANDED_OFF_RESULT;
    }

    /**
     * 재처리가 실패했다. OPEN 그대로 두고 시도 횟수 · 시각 · 실패 이유(본문 없는 값, {@value #RESULT_LENGTH}자까지)만 남긴다.
     */
    public void recordReprocessFailure(String reason, LocalDateTime now) {
        requireOpen();
        this.reprocessCount++;
        this.lastReprocessedAt = now;
        this.lastReprocessResult = reason == null || reason.length() <= RESULT_LENGTH
            ? reason : reason.substring(0, RESULT_LENGTH);
    }

    /**
     * 확인 후 종료한다(처리하지 않음). 메모는 서비스 입력 검증이 비어 있지 않게 한다. OPEN만(서비스가 먼저 거른다).
     */
    public void close(Long adminId, String memo, LocalDateTime now) {
        requireOpen();
        this.status = Status.CLOSED;
        this.handledBy = adminId;
        this.handledAt = now;
        this.memo = memo;
    }

    /**
     * 결제 토픽에서 온 메시지인지(#632). 결제 완료가 빠지면 돈은 빠졌는데 주문이 결제대기 · 만료 취소될 수 있어 먼저 본다.
     */
    public boolean isPaymentEvent() {
        return PAYMENT_SOURCE_TOPIC.equals(sourceTopic);
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("OPEN인 DLT 메시지만 처리할 수 있습니다: status=" + status);
        }
    }
}
