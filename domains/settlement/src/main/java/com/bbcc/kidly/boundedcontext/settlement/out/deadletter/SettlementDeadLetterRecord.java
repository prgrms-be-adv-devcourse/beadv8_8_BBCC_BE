package com.bbcc.kidly.boundedcontext.settlement.out.deadletter;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.State;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 기술적인 DLT 원본 보관. 읽기는 JPA, 충돌 없는 수신과 조건부 결과 기록은 저장소 어댑터(SettlementDeadLetterStore)가 맡는다.
 */
@Entity(name = "SettlementDeadLetterRecord")
@Table(schema = "settlement", name = "settlement_dead_letter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementDeadLetterRecord extends BaseTimeEntity {

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

    @Column(columnDefinition = "bytea")
    private byte[] payload;

    @Column(length = 255)
    private String failureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    private int replayCount;
    private Long lastReplayedBy;
    private LocalDateTime lastReplayedAt;

    @Column(length = 255)
    private String lastReplayFailureType;
}
