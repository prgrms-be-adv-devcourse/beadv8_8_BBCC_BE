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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 정산 작업 1회의 기록. 종료 기준일에서 정산 기간을 정하고, 작업이 끝나면 성공 · 건너뛴 판매자 수를 남긴다.
 * 실행 중에는 running_key가 채워져 유니크 제약으로 실행 중인 기록이 1건만 있게 한다.
 */
@Entity
@Table(
    name = "settlement_run", schema = "settlement",
    uniqueConstraints = @UniqueConstraint(name = "uk_settlement_run_running_key", columnNames = "running_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRun extends BaseTimeEntity {

    private static final String RUNNING_KEY = "RUNNING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cutoff_at", nullable = false)
    private LocalDateTime cutoffAt;

    @Column(name = "period_start_on", nullable = false)
    private LocalDate periodStartOn;

    @Column(name = "period_end_on", nullable = false)
    private LocalDate periodEndOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private SettlementTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementRunStatus status;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    // 수동 실행한 관리자 ID. 자동 실행이면 null
    @Column(name = "requested_by")
    private Long requestedBy;

    // 실행 중에만 값이 있다. 끝나면 null이라 유니크 제약에 걸리지 않는다
    @Column(name = "running_key", length = 10)
    private String runningKey;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // 끝나기 전에는 null
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Version
    private Long version;

    private SettlementRun(
        LocalDateTime cutoffAt, SettlementTriggerType triggerType, Long requestedBy, LocalDateTime now) {
        this.cutoffAt = cutoffAt;
        this.periodEndOn = cutoffAt.toLocalDate().minusDays(1);
        this.periodStartOn = periodEndOn.withDayOfMonth(1);
        this.triggerType = triggerType;
        this.requestedBy = requestedBy;
        this.status = SettlementRunStatus.RUNNING;
        this.runningKey = RUNNING_KEY;
        this.startedAt = now;
    }

    // 종료 기준일은 날짜의 0시. 기간은 종료일 = 기준일 − 1일, 시작일 = 그 달 1일. 수동 실행만 관리자 ID가 있다
    public static SettlementRun start(
        LocalDateTime cutoffAt, SettlementTriggerType triggerType, Long requestedBy, LocalDateTime now) {
        requireNonNull(cutoffAt, "cutoffAt은 필수입니다");
        requireNonNull(triggerType, "triggerType은 필수입니다");
        requireNonNull(now, "now는 필수입니다");
        Assert.isTrue(cutoffAt.toLocalTime().equals(LocalTime.MIDNIGHT), "종료 기준일은 0시여야 합니다");
        if (triggerType == SettlementTriggerType.MANUAL) {
            Assert.isTrue(requestedBy != null && requestedBy > 0, "수동 실행은 관리자 ID가 필요합니다");
        } else {
            Assert.isTrue(requestedBy == null, "자동 실행은 관리자 ID가 없어야 합니다");
        }
        return new SettlementRun(cutoffAt, triggerType, requestedBy, now);
    }

    // 건너뛴 판매자가 있으면 PARTIAL_FAILED
    public void finish(int successCount, int failCount, LocalDateTime now) {
        end(failCount > 0 ? SettlementRunStatus.PARTIAL_FAILED : SettlementRunStatus.COMPLETED,
            successCount, failCount, now);
    }

    public void fail(int successCount, int failCount, LocalDateTime now) {
        end(SettlementRunStatus.FAILED, successCount, failCount, now);
    }

    private void end(SettlementRunStatus result, int successCount, int failCount, LocalDateTime now) {
        requireNonNull(now, "now는 필수입니다");
        Assert.isTrue(successCount >= 0 && failCount >= 0, "판매자 수는 0 이상이어야 합니다");
        Assert.state(status == SettlementRunStatus.RUNNING, "실행 중인 정산만 끝낼 수 있습니다");
        this.status = result;
        this.successCount = successCount;
        this.failCount = failCount;
        this.runningKey = null;
        this.finishedAt = now;
    }
}
