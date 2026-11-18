package com.bbcc.kidly.member.support.deadletter;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DLT에서 받은 메시지와 재처리 이력. 도메인 모듈은 자기 스키마의 {@code dead_letter} 표에 매핑한 하위 엔티티를 둔다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DeadLetter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String dltTopic;

    private int dltPartition;
    private long dltOffset;

    @Column(nullable = false, length = DeadLetterMessage.SOURCE_TOPIC_LENGTH)
    private String sourceTopic;

    private Integer sourcePartition;
    private Long sourceOffset;

    @Column(length = DeadLetterMessage.HEADER_LENGTH)
    private String messageKey;

    @Column(length = DeadLetterMessage.HEADER_LENGTH)
    private String eventType;

    @Column(length = DeadLetterMessage.HEADER_LENGTH)
    private String eventId;

    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "bytea")
    private byte[] payload;

    @Column(length = DeadLetterMessage.FAILURE_TYPE_LENGTH)
    private String failureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadLetterStatus status;

    private int replayCount;
    private Long lastReplayedBy;
    private LocalDateTime lastReplayedAt;

    @Column(length = 255)
    private String lastReplayFailureType;

    protected DeadLetter(DeadLetterMessage message) {
        dltTopic = message.dltTopic();
        dltPartition = message.dltPartition();
        dltOffset = message.dltOffset();
        sourceTopic = message.sourceTopic();
        sourcePartition = message.sourcePartition();
        sourceOffset = message.sourceOffset();
        messageKey = message.messageKey();
        eventType = message.eventType();
        eventId = message.eventId();
        payload = message.payload() == null ? null : message.payload().getBytes(UTF_8);
        failureType = message.failureType();
        status = DeadLetterStatus.OPEN;
    }

    public String getPayload() {
        return payload == null ? null : new String(payload, UTF_8);
    }

    public boolean isReprocessed() {
        return status == DeadLetterStatus.REPROCESSED;
    }

    public void markReprocessed(Long adminId, LocalDateTime now) {
        status = DeadLetterStatus.REPROCESSED;
        recordReplay(adminId, now, null);
    }

    public void recordReplayFailure(Long adminId, LocalDateTime now, String failureType) {
        recordReplay(adminId, now, failureType);
    }

    private void recordReplay(Long adminId, LocalDateTime now, String failureType) {
        replayCount++;
        lastReplayedBy = adminId;
        lastReplayedAt = now;
        lastReplayFailureType = failureType;
    }
}
