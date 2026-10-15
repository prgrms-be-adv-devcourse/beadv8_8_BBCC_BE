package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.util.Assert;

/**
 * 정산 기간. 종료 기준일 0시 전에 확정된 대상을 정산하고, 정산서 기간은 기준일 전날이 속한 달 1일부터 그 전날까지다.
 */
public record SettlementPeriod(LocalDateTime cutoffAt, LocalDate startOn, LocalDate endOn) {

    public static SettlementPeriod from(LocalDateTime cutoffAt) {
        requireNonNull(cutoffAt, "cutoffAt은 필수입니다");
        Assert.isTrue(cutoffAt.toLocalTime().equals(LocalTime.MIDNIGHT), "종료 기준일은 0시여야 합니다");
        var endOn = cutoffAt.toLocalDate().minusDays(1);
        return new SettlementPeriod(cutoffAt, endOn.withDayOfMonth(1), endOn);
    }
}
