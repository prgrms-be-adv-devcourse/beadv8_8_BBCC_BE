package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.util.Assert;

/**
 * 정산 기간. 종료 기준일 0시 전에 확정된 대상을 정산하고, 정산서 종료일은 기준일 전날이다.
 * 시작일은 정산서마다 그 정산서에 들어간 대상으로 정한다(SettlementCalculator).
 */
public record SettlementPeriod(LocalDateTime cutoffAt, LocalDate endOn) {

    public static SettlementPeriod from(LocalDateTime cutoffAt) {
        requireNonNull(cutoffAt, "cutoffAt은 필수입니다");
        Assert.isTrue(cutoffAt.toLocalTime().equals(LocalTime.MIDNIGHT), "종료 기준일은 0시여야 합니다");
        return new SettlementPeriod(cutoffAt, cutoffAt.toLocalDate().minusDays(1));
    }

    // 수동 실행 기준일은 오늘 또는 지난 날짜
    public static SettlementPeriod manual(LocalDate cutoffOn, LocalDate today) {
        requireNonNull(cutoffOn, "cutoffOn은 필수입니다");
        requireNonNull(today, "today는 필수입니다");
        if (cutoffOn.isAfter(today)) {
            throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_CUTOFF);
        }
        return from(cutoffOn.atStartOfDay());
    }

    // 기준은 실행일 0시라 전날까지 확정된 대상을 정산한다
    public static SettlementPeriod daily(LocalDate today) {
        requireNonNull(today, "today는 필수입니다");
        return from(today.atStartOfDay());
    }
}
