package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatementRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SettlementItemRepositoryIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired
    private SettlementItemRepository itemRepository;

    @Autowired
    private SettlementStatementRepository statementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesItemAndFindsByOrderItemId() {
        SettlementItem saved = itemRepository.save(newItem(101L));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(itemRepository.findById(saved.getId())).isPresent();
        assertThat(itemRepository.findByOrderItemId(101L)).hasValueSatisfying(found -> {
            assertThat(found.getStatus()).isEqualTo(SettlementItemStatus.PENDING);
            assertThat(found.getEligibleAt()).isEqualTo(CONFIRMED_AT.plusDays(14));
            assertThat(found.getFeeAmount()).isNull();
        });
    }

    @Test
    void preventsDuplicateOrderItemId() {
        itemRepository.save(newItem(102L));

        assertThatThrownBy(() -> itemRepository.save(newItem(102L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsStatementForeignKey() {
        SettlementStatement statement = statementRepository.save(SettlementStatement.create(
                new SettlementStatementCreateInfo(
                        10L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                        10_000L, 1_000L, 9_000L)));
        SettlementItem item = newItem(103L);
        item.markReady(CONFIRMED_AT.plusDays(14));
        item.assignToStatement(statement, 1_000L);
        itemRepository.save(item);

        Long referencedId = jdbcTemplate.queryForObject(
                "SELECT settlement_statement_id FROM settlement.settlement_item WHERE order_item_id = ?",
                Long.class, 103L);

        assertThat(referencedId).isEqualTo(statement.getId());
        assertThat(itemRepository.findByOrderItemId(103L))
                .hasValueSatisfying(found -> assertThat(found.getStatus())
                        .isEqualTo(SettlementItemStatus.SETTLED));
    }

    private SettlementItem newItem(Long orderItemId) {
        return SettlementItem.create(new SettlementItemCreateInfo(
                1L, orderItemId, 10L, 10_000L, CONFIRMED_AT));
    }
}
