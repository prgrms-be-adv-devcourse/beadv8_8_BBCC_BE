package com.bbcc.kidly.boundedcontext.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementItemTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Test
    void createSetsPendingStatusAndEligibleAtAfterFourteenDays() {
        SettlementItem item = SettlementItem.create(validInfo());

        assertEquals(SettlementItemStatus.PENDING, item.getStatus());
        assertEquals(CONFIRMED_AT, item.getConfirmedAt());
        assertEquals(CONFIRMED_AT.plusDays(14), item.getEligibleAt());
        assertEquals(Long.valueOf(1L), item.getOrderId());
        assertEquals(Long.valueOf(101L), item.getOrderItemId());
        assertEquals(Long.valueOf(10L), item.getSellerId());
        assertEquals(Long.valueOf(10_000L), item.getSalesAmount());
        assertNull(item.getFeeAmount());
        assertNull(item.getSettlementAmount());
    }

    @Test
    void cannotBecomeReadyBeforeFourteenDays() {
        SettlementItem item = SettlementItem.create(validInfo());

        assertThrows(IllegalArgumentException.class,
                () -> item.markReady(CONFIRMED_AT.plusDays(14).minusNanos(1)));
        assertEquals(SettlementItemStatus.PENDING, item.getStatus());
    }

    @Test
    void canBecomeReadyAtExactlyFourteenDays() {
        SettlementItem item = SettlementItem.create(validInfo());

        item.markReady(CONFIRMED_AT.plusDays(14));

        assertEquals(SettlementItemStatus.READY, item.getStatus());
    }

    @Test
    void markingReadyTwiceIsIdempotent() {
        SettlementItem item = SettlementItem.create(validInfo());
        item.markReady(CONFIRMED_AT.plusDays(14));

        item.markReady(CONFIRMED_AT.plusDays(15));

        assertEquals(SettlementItemStatus.READY, item.getStatus());
    }

    @Test
    void excludedItemCannotBecomeReady() {
        SettlementItem item = SettlementItem.create(validInfo());
        item.exclude();

        assertThrows(IllegalStateException.class,
                () -> item.markReady(CONFIRMED_AT.plusDays(14)));
        assertEquals(SettlementItemStatus.EXCLUDED, item.getStatus());
    }

    @Test
    void readyItemCanBeExcluded() {
        SettlementItem item = SettlementItem.create(validInfo());
        item.markReady(CONFIRMED_AT.plusDays(14));

        item.exclude();
        item.exclude(); // 중복 취소·환불 알림을 받아도 상태를 유지한다.

        assertEquals(SettlementItemStatus.EXCLUDED, item.getStatus());
    }

    @Test
    void rejectsInvalidIdentifiersAndSalesAmount() {
        assertThrows(IllegalArgumentException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(0L, 101L, 10L, 10_000L, CONFIRMED_AT)));
        assertThrows(IllegalArgumentException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(1L, -1L, 10L, 10_000L, CONFIRMED_AT)));
        assertThrows(IllegalArgumentException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(1L, 101L, 0L, 10_000L, CONFIRMED_AT)));
        assertThrows(IllegalArgumentException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(1L, 101L, 10L, -100L, CONFIRMED_AT)));
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(NullPointerException.class, () -> SettlementItem.create(null));
        assertThrows(NullPointerException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(1L, 101L, 10L, 10_000L, null)));
        assertThrows(NullPointerException.class, () -> SettlementItem.create(
                new SettlementItemCreateInfo(null, 101L, 10L, 10_000L, CONFIRMED_AT)));
    }

    private SettlementItemCreateInfo validInfo() {
        return new SettlementItemCreateInfo(1L, 101L, 10L, 10_000L, CONFIRMED_AT);
    }
}
