package com.bbcc.kidly.boundedcontext.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SettlementStatementTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    @Test
    void createsMonthlyStatementWithTotalsAndCreatedStatus() {
        SettlementStatement statement = SettlementStatement.create(validInfo());

        assertEquals(Long.valueOf(10L), statement.getSellerId());
        assertEquals(PERIOD_START, statement.getPeriodStartOn());
        assertEquals(PERIOD_END, statement.getPeriodEndOn());
        assertEquals(Long.valueOf(10_000L), statement.getTotalSalesAmount());
        assertEquals(Long.valueOf(1_000L), statement.getTotalFeeAmount());
        assertEquals(Long.valueOf(9_000L), statement.getTotalSettlementAmount());
        assertEquals(SettlementStatementStatus.CREATED, statement.getStatus());
    }

    @Test
    void rejectsPartialMonthPeriod() {
        assertThrows(IllegalArgumentException.class, () -> SettlementStatement.create(
                new SettlementStatementCreateInfo(10L, LocalDate.of(2026, 10, 2),
                        PERIOD_END, 10_000L, 1_000L, 9_000L)));
        assertThrows(IllegalArgumentException.class, () -> SettlementStatement.create(
                new SettlementStatementCreateInfo(10L, PERIOD_START,
                        LocalDate.of(2026, 10, 30), 10_000L, 1_000L, 9_000L)));
    }

    @Test
    void rejectsInconsistentAmounts() {
        assertThrows(IllegalArgumentException.class, () -> SettlementStatement.create(
                new SettlementStatementCreateInfo(10L, PERIOD_START,
                        PERIOD_END, 10_000L, 1_000L, 8_000L)));
        assertThrows(IllegalArgumentException.class, () -> SettlementStatement.create(
                new SettlementStatementCreateInfo(10L, PERIOD_START,
                        PERIOD_END, 10_000L, 11_000L, 0L)));
    }

    @Test
    void confirmsAndMarksPaid() {
        SettlementStatement statement = SettlementStatement.create(validInfo());

        statement.confirm();
        assertEquals(SettlementStatementStatus.CONFIRMED, statement.getStatus());
        statement.markPaid();
        assertEquals(SettlementStatementStatus.PAID, statement.getStatus());
    }

    @Test
    void recordsPaymentFailure() {
        SettlementStatement statement = SettlementStatement.create(validInfo());
        statement.confirm();

        statement.markPaymentFailed();

        assertEquals(SettlementStatementStatus.PAYMENT_FAILED, statement.getStatus());
    }

    @Test
    void rejectsInvalidStatusTransitions() {
        SettlementStatement statement = SettlementStatement.create(validInfo());

        assertThrows(IllegalStateException.class, statement::markPaid);
        assertThrows(IllegalStateException.class, statement::markPaymentFailed);
        statement.confirm();
        assertThrows(IllegalStateException.class, statement::confirm);
        statement.markPaid();
        assertThrows(IllegalStateException.class, statement::markPaymentFailed);
    }

    private SettlementStatementCreateInfo validInfo() {
        return new SettlementStatementCreateInfo(
                10L, PERIOD_START, PERIOD_END, 10_000L, 1_000L, 9_000L);
    }
}
