package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.FEE_RATE;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementSellerPlan;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementSellerProcessor;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementCalculator;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementSellerProcessorTest {

    @Mock
    private SettlementItemRepository settlementItemRepository;

    private SettlementSellerProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SettlementSellerProcessor(
            settlementItemRepository, new SettlementCalculator(FEE_RATE), SettlementPeriod.from(CUTOFF_AT));
    }

    @Test
    @DisplayName("판매자의 대기 항목을 ID 오름차순 · 판매 합계의 정산 계획으로 만든다")
    void plan() {
        givenPendingItems(item(7L, 10_000L), item(3L, 5_009L));

        var plan = processor.process(SELLER_ID);

        assertThat(plan).isEqualTo(new SettlementSellerPlan(SELLER_ID, List.of(3L, 7L), 15_009L));
    }

    @Test
    @DisplayName("정산할 항목이 없으면 null로 걸러낸다")
    void noItems() {
        givenPendingItems();

        assertThat(processor.process(SELLER_ID)).isNull();
    }

    @Test
    @DisplayName("판매 합계가 long을 넘으면 ArithmeticException이다")
    void overflow() {
        givenPendingItems(item(1L, Long.MAX_VALUE), item(2L, Long.MAX_VALUE));

        assertThatThrownBy(() -> processor.process(SELLER_ID)).isInstanceOf(ArithmeticException.class);
    }

    private void givenPendingItems(SettlementItem... items) {
        given(settlementItemRepository.findAllBySellerIdAndStatusAndConfirmedAtBefore(
            SELLER_ID, SettlementItemStatus.PENDING, CUTOFF_AT)).willReturn(List.of(items));
    }

    private static SettlementItem item(Long id, long salesAmount) {
        var item = SettlementFixture.item(id, SELLER_ID, salesAmount, CONFIRMED_AT);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
