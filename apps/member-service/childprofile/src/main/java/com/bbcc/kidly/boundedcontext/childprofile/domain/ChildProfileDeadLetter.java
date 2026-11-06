package com.bbcc.kidly.boundedcontext.childprofile.domain;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 프로필 처리기가 처리하지 못해 DLT로 간 메시지. DLT 위치(토픽 · 파티션 · 오프셋)당 하나다.
 */
@Entity
@Table(name = "dead_letter", schema = "childprofile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfileDeadLetter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String dltTopic;

    private int dltPartition;
    private long dltOffset;

    @Column(nullable = false, length = 100)
    private String sourceTopic;

    private Integer sourcePartition;
    private Long sourceOffset;

    @Column(length = 100)
    private String messageKey;

    @Column(length = 100)
    private String eventType;

    @Column(length = 100)
    private String eventId;

    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "bytea")
    private byte[] payload;

    @Column(length = 255)
    private String failureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeadLetterStatus status;

    private int replayCount;
    private Long lastReplayedBy;
    private LocalDateTime lastReplayedAt;

    @Column(length = 255)
    private String lastReplayFailureType;

    public static ChildProfileDeadLetter received(String dltTopic, int dltPartition, long dltOffset, String sourceTopic,
        Integer sourcePartition, Long sourceOffset, String messageKey, String eventType, String eventId,
        String payload, String failureType) {
        var deadLetter = new ChildProfileDeadLetter();
        deadLetter.dltTopic = dltTopic;
        deadLetter.dltPartition = dltPartition;
        deadLetter.dltOffset = dltOffset;
        deadLetter.sourceTopic = sourceTopic;
        deadLetter.sourcePartition = sourcePartition;
        deadLetter.sourceOffset = sourceOffset;
        deadLetter.messageKey = messageKey;
        deadLetter.eventType = eventType;
        deadLetter.eventId = eventId;
        deadLetter.payload = payload == null ? null : payload.getBytes(UTF_8);
        deadLetter.failureType = failureType;
        deadLetter.status = DeadLetterStatus.OPEN;
        return deadLetter;
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
