package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class SettlementItemRepositoryIntegrationTest {

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Test
    @DisplayName("저장한 항목을 ID와 주문상품 ID로 찾는다")
    void saveAndFind() {
        var saved = settlementItemRepository.save(SettlementFixture.item(101L));

        assertThat(settlementItemRepository.findById(saved.getId()))
            .hasValueSatisfying(found -> assertThat(found.getStatus()).isEqualTo(SettlementItemStatus.PENDING));
        assertThat(settlementItemRepository.existsByOrderItemId(101L)).isTrue();
        assertThat(settlementItemRepository.existsByOrderItemId(999L)).isFalse();
    }

    @Test
    @DisplayName("같은 주문상품은 두 번 저장되지 않는다")
    void uniqueOrderItem() {
        settlementItemRepository.save(SettlementFixture.item(102L));

        assertThatThrownBy(() -> settlementItemRepository.save(SettlementFixture.item(102L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정산한 항목은 정산서 ID와 상태가 저장된다")
    void saveSettled() {
        var item = SettlementFixture.item(103L);
        item.settle(7L);
        var saved = settlementItemRepository.save(item);

        assertThat(settlementItemRepository.findById(saved.getId())).hasValueSatisfying(found -> {
            assertThat(found.getStatus()).isEqualTo(SettlementItemStatus.SETTLED);
            assertThat(found.getSettlementId()).isEqualTo(7L);
        });
    }
}
