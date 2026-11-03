package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class SettlementTargetQueryIntegrationTest {

    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private SettlementTargetQuery settlementTargetQuery;

    @BeforeEach
    void setUp() {
        settlementItemRepository.save(SettlementFixture.item(201L, 10L, 10_000L, EARLIER));
        settlementItemRepository.save(SettlementFixture.item(202L, 10L, 5_000L, LATER));
        var settled = SettlementFixture.item(203L, 10L, 7_000L, LATER);
        settled.settle(7L);
        settlementItemRepository.save(settled);
        settlementItemRepository.save(SettlementFixture.item(204L, 11L, 3_000L, LATER));
    }

    @Test
    @DisplayName("본인의 대기 항목만 확정 시각 역순으로 찾는다")
    void findPending() {
        var page = settlementTargetQuery.findPending(10L, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting("orderItemId").containsExactly(202L, 201L);
        assertThat(page.getContent().getFirst().salesAmount()).isEqualTo(5_000L);
        assertThat(page.getContent().getFirst().confirmedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("페이지 크기만큼만 담고 전체 건수와 다음 페이지 여부를 준다")
    void paging() {
        var page = settlementTargetQuery.findPending(10L, PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
    }
}
