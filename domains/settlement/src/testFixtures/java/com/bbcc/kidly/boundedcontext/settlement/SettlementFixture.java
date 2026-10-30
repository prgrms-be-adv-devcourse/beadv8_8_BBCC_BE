package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class SettlementFixture {

    public static final Long SELLER_ID = 10L;
    public static final Long ORDER_ID = 1L;
    public static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 0, 0);
    public static final BigDecimal FEE_RATE = new BigDecimal("0.1000");
    public static final LocalDateTime CUTOFF_AT = LocalDateTime.of(2026, 11, 1, 0, 0);
    public static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    public static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    private SettlementFixture() {
    }

    public static SettlementItemCreateInfo itemInfo(Long orderItemId) {
        return new SettlementItemCreateInfo(ORDER_ID, orderItemId, SELLER_ID, 10_000L, CONFIRMED_AT);
    }

    // 아직 보내지 않은 settlement.payout-requested 아웃박스 메시지. 키는 정산서 ID(31)
    public static SettlementOutboxMessage outboxMessage() {
        return SettlementOutboxMessage.create(UUID.randomUUID(), "settlement.payout-requested",
            "kidly.settlement.events", "31", "{\"eventType\":\"settlement.payout-requested\"}");
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

    public static Settlement settlement(Long sellerId, LocalDate startOn, LocalDate endOn) {
        return Settlement.create(new SettlementCreateInfo(sellerId, startOn, endOn, 100_000L, FEE_RATE), NOW);
    }

    public static SettlementItem item(Long orderItemId, Long sellerId, long salesAmount, LocalDateTime confirmedAt) {
        return SettlementItem.create(
            new SettlementItemCreateInfo(ORDER_ID, orderItemId, sellerId, salesAmount, confirmedAt));
    }
}
