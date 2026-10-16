package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 정산 실행에서 건너뛴 판매자 한 명. 그 판매자의 대상은 대기로 남아 다음 실행에서 다시 정산된다.
 * 기록 시각은 created_at이다.
 */
@Entity
@Table(
    name = "settlement_skip", schema = "settlement",
    indexes = @Index(name = "idx_settlement_skip_job_execution_id", columnList = "job_execution_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementSkip extends BaseTimeEntity {

    private static final int REASON_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Batch 작업 실행 ID (정산 실행 runId)
    @Column(name = "job_execution_id", nullable = false)
    private Long jobExecutionId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private SettlementSkipPhase phase;

    // 예외 이름: 메시지. 200자에서 자른다
    @Column(name = "reason", nullable = false, length = REASON_MAX_LENGTH)
    private String reason;

    private SettlementSkip(Long jobExecutionId, Long sellerId, SettlementSkipPhase phase, String reason) {
        this.jobExecutionId = jobExecutionId;
        this.sellerId = sellerId;
        this.phase = phase;
        this.reason = reason;
    }

    public static SettlementSkip create(SettlementSkipCreateInfo info) {
        requireNonNull(info, "info는 필수입니다");
        var jobExecutionId = requireNonNull(info.jobExecutionId(), "jobExecutionId는 필수입니다");
        Assert.isTrue(jobExecutionId > 0, "jobExecutionId는 0보다 커야 합니다");
        var sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        Assert.isTrue(sellerId > 0, "sellerId는 0보다 커야 합니다");
        return new SettlementSkip(jobExecutionId, sellerId, requireNonNull(info.phase(), "phase는 필수입니다"),
            reasonOf(requireNonNull(info.error(), "error는 필수입니다")));
    }

    private static String reasonOf(Throwable error) {
        var name = error.getClass().getSimpleName();
        var reason = error.getMessage() == null ? name : name + ": " + error.getMessage();
        return reason.length() <= REASON_MAX_LENGTH ? reason : reason.substring(0, REASON_MAX_LENGTH);
    }
}
