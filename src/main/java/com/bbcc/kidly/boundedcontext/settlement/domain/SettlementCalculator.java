package com.bbcc.kidly.boundedcontext.settlement.domain;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.Assert;

/**
 * 판매자 1명의 정산 대상 판매 합계를 구하고 정산 기간의 정산서를 만든다.
 * 대상은 모두 같은 판매자 · 대기 상태 · 종료 기준일 이전이어야 한다.
 */
public class SettlementCalculator {

    private final BigDecimal feeRate;

    public SettlementCalculator(BigDecimal feeRate) {
        this.feeRate = requireNonNull(feeRate, "feeRate는 필수입니다");
    }

    // 판매 합계가 long을 넘치면 ArithmeticException
    public long sumSalesAmount(SettlementPeriod period, List<SettlementItem> items) {
        requireNonNull(period, "period는 필수입니다");
        requireNonNull(items, "items는 필수입니다");
        Assert.notEmpty(items, "정산 대상이 없습니다");
        var sellerId = items.getFirst().getSellerId();
        var total = 0L;
        for (var item : items) {
            Assert.isTrue(item.getSellerId().equals(sellerId), "한 판매자의 정산 대상만 묶을 수 있습니다");
            Assert.isTrue(
                item.getStatus() == SettlementItemStatus.PENDING, "대기 중인 정산 대상만 정산할 수 있습니다");
            Assert.isTrue(
                item.getConfirmedAt().isBefore(period.cutoffAt()), "종료 기준일 전에 확정된 대상만 정산할 수 있습니다");
            total = Math.addExact(total, item.getSalesAmount());
        }
        return total;
    }

    public Settlement create(SettlementPeriod period, Long sellerId, long totalSalesAmount, LocalDateTime now) {
        requireNonNull(period, "period는 필수입니다");
        return Settlement.create(
            new SettlementCreateInfo(sellerId, period.startOn(), period.endOn(), totalSalesAmount, feeRate), now);
    }
}
