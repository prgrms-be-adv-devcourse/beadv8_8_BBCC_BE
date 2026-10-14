package com.bbcc.kidly.boundedcontext.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementCreateInfo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementItemAssignmentTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Test
    void assignsReadyItemToStatementAndCalculatesAmount() {
        SettlementItem item = readyItem();
        SettlementStatement statement = statementForSeller(10L);

        item.assignToStatement(statement, 1_000L);

        assertEquals(SettlementItemStatus.SETTLED, item.getStatus());
        assertEquals(Long.valueOf(1_000L), item.getFeeAmount());
        assertEquals(Long.valueOf(9_000L), item.getSettlementAmount());
        assertSame(statement, item.getSettlementStatement());
    }

    @Test
    void rejectsPendingOrRepeatedAssignment() {
        SettlementItem pending = SettlementItem.create(validInfo());
        SettlementStatement statement = statementForSeller(10L);
        assertThrows(IllegalStateException.class, () -> pending.assignToStatement(statement, 1_000L));

        SettlementItem ready = readyItem();
        ready.assignToStatement(statement, 1_000L);
        assertThrows(IllegalStateException.class, () -> ready.assignToStatement(statement, 1_000L));
    }

    @Test
    void rejectsDifferentSellerAndExcessFee() {
        SettlementItem item = readyItem();
        assertThrows(IllegalArgumentException.class,
                () -> item.assignToStatement(statementForSeller(20L), 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> item.assignToStatement(statementForSeller(10L), 10_001L));
    }

    @Test
    void rejectsConfirmedStatementAndWrongPeriod() {
        SettlementItem item = readyItem();
        SettlementStatement confirmedStatement = statementForSeller(10L);
        confirmedStatement.confirm();
        assertThrows(IllegalStateException.class,
                () -> item.assignToStatement(confirmedStatement, 1_000L));

        SettlementStatement september = SettlementStatement.create(new SettlementStatementCreateInfo(
                10L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                10_000L, 1_000L, 9_000L));
        assertThrows(IllegalArgumentException.class, () -> item.assignToStatement(september, 1_000L));
    }

    private SettlementItem readyItem() {
        SettlementItem item = SettlementItem.create(validInfo());
        item.markReady(CONFIRMED_AT.plusDays(14));
        return item;
    }

    private SettlementItemCreateInfo validInfo() {
        return new SettlementItemCreateInfo(1L, 101L, 10L, 10_000L, CONFIRMED_AT);
    }

    private SettlementStatement statementForSeller(Long sellerId) {
        return SettlementStatement.create(new SettlementStatementCreateInfo(
                sellerId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                10_000L, 1_000L, 9_000L));
    }
}
