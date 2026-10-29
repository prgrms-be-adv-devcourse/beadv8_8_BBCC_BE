package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.settlement.app.AdminSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementPayoutRetryScheduler;
import com.bbcc.kidly.boundedcontext.settlement.domain.Settlement;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementErrorCode;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementPayoutRetrySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private AdminSettlementService adminSettlementService;

    @InjectMocks
    private SettlementPayoutRetryScheduler scheduler;

    @Test
    @DisplayName("30분 넘게 지급 결과가 없고 7일 안인 정산서마다 지급을 다시 요청한다")
    void requestsStuckInWindow() {
        givenStuck(settlement(1L), settlement(2L));

        int count = scheduler.retryStuckPayouts(NOW);

        assertThat(count).isEqualTo(2);
        then(settlementRepository).should().findByStatusAndCompletedAtBetweenOrderByIdAsc(
            SettlementStatus.COMPLETED, NOW.minusDays(7), NOW.minusMinutes(30), Limit.of(100));
        then(adminSettlementService).should().retryPayout(1L);
        then(adminSettlementService).should().retryPayout(2L);
    }

    @Test
    @DisplayName("그 사이 지급 결과를 받은 정산서는 건너뛰고 다음 건을 요청한다")
    void skipsAlreadyResolved() {
        givenStuck(settlement(1L), settlement(2L));
        willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_RETRYABLE))
            .given(adminSettlementService).retryPayout(1L);

        int count = scheduler.retryStuckPayouts(NOW);

        assertThat(count).isEqualTo(1);
        then(adminSettlementService).should().retryPayout(2L);
    }

    @Test
    @DisplayName("한 건이 예상 못 한 오류로 실패해도 다음 건을 요청한다")
    void continuesAfterUnexpectedError() {
        givenStuck(settlement(1L), settlement(2L));
        willThrow(new RuntimeException("boom")).given(adminSettlementService).retryPayout(1L);

        int count = scheduler.retryStuckPayouts(NOW);

        assertThat(count).isEqualTo(1);
        then(adminSettlementService).should().retryPayout(2L);
        then(adminSettlementService).should(org.mockito.Mockito.times(2)).retryPayout(anyLong());
    }

    private void givenStuck(Settlement... settlements) {
        given(settlementRepository.findByStatusAndCompletedAtBetweenOrderByIdAsc(
            SettlementStatus.COMPLETED, NOW.minusDays(7), NOW.minusMinutes(30), Limit.of(100)))
            .willReturn(List.of(settlements));
    }

    private Settlement settlement(Long id) {
        var settlement = SettlementFixture.settlement(10_000L);
        ReflectionTestUtils.setField(settlement, "id", id);
        return settlement;
    }
}
