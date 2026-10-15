package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.util.Assert;

/**
 * 정산 기간. 종료 기준일 0시 전에 확정된 대상을 정산하고, 정산서 기간은 기준일 전날이 속한 달 1일부터 그 전날까지다.
 */
public record SettlementPeriod(LocalDateTime cutoffAt, LocalDate startOn, LocalDate endOn) {

    // 기준일 · 오늘은 이 시간대의 날짜다
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static SettlementPeriod from(LocalDateTime cutoffAt) {
        requireNonNull(cutoffAt, "cutoffAt은 필수입니다");
        Assert.isTrue(cutoffAt.toLocalTime().equals(LocalTime.MIDNIGHT), "종료 기준일은 0시여야 합니다");
        var endOn = cutoffAt.toLocalDate().minusDays(1);
        return new SettlementPeriod(cutoffAt, endOn.withDayOfMonth(1), endOn);
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

    // 기준은 그 달 1일 0시라 지난달 확정분을 정산한다
    public static SettlementPeriod monthly(LocalDate today) {
        requireNonNull(today, "today는 필수입니다");
        return from(today.withDayOfMonth(1).atStartOfDay());
    }
}
