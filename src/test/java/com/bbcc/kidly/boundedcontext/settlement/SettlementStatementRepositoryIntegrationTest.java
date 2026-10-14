package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class SettlementStatementRepositoryIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    @Autowired
    private SettlementStatementRepository statementRepository;

    @Test
    void savesAndFindsMonthlyStatement() {
        SettlementStatement saved = statementRepository.save(newStatement(10L));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(statementRepository.findById(saved.getId())).isPresent();
        assertThat(statementRepository.findBySellerIdAndPeriodStartOn(10L, PERIOD_START))
                .hasValueSatisfying(found -> {
                    assertThat(found.getStatus()).isEqualTo(SettlementStatementStatus.CREATED);
                    assertThat(found.getTotalSettlementAmount()).isEqualTo(9_000L);
                });
    }

    @Test
    void preventsDuplicateSellerAndMonth() {
        statementRepository.save(newStatement(20L));

        assertThatThrownBy(() -> statementRepository.save(newStatement(20L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsConfirmedStatus() {
        SettlementStatement saved = statementRepository.save(newStatement(30L));
        saved.confirm();
        statementRepository.save(saved);

        assertThat(statementRepository.findById(saved.getId()))
                .hasValueSatisfying(found -> assertThat(found.getStatus())
                        .isEqualTo(SettlementStatementStatus.CONFIRMED));
    }

    private SettlementStatement newStatement(Long sellerId) {
        return SettlementStatement.create(new SettlementStatementCreateInfo(
                sellerId, PERIOD_START, PERIOD_END, 10_000L, 1_000L, 9_000L));
    }
}
