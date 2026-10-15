package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SettlementFixture {

    public static final Long SELLER_ID = 10L;
    public static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 0);
    public static final BigDecimal FEE_RATE = new BigDecimal("0.1000");
    public static final LocalDateTime CUTOFF_AT = LocalDateTime.of(2026, 11, 1, 0, 0);
    public static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    public static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    private SettlementFixture() {
    }

    public static SettlementItemCreateInfo itemInfo(Long orderItemId) {
        return new SettlementItemCreateInfo(1L, orderItemId, SELLER_ID, 10_000L, CONFIRMED_AT);
    }

    public static SettlementItem item(Long orderItemId) {
        return SettlementItem.create(itemInfo(orderItemId));
    }

    public static SettlementCreateInfo settlementInfo(long totalSalesAmount) {
        return new SettlementCreateInfo(SELLER_ID, PERIOD_START, PERIOD_END, totalSalesAmount, FEE_RATE);
    }

    public static Settlement settlement(long totalSalesAmount) {
        return Settlement.create(settlementInfo(totalSalesAmount), NOW);
    }

    public static SettlementItem item(Long orderItemId, Long sellerId, long salesAmount, LocalDateTime confirmedAt) {
        return SettlementItem.create(new SettlementItemCreateInfo(1L, orderItemId, sellerId, salesAmount, confirmedAt));
    }
}
