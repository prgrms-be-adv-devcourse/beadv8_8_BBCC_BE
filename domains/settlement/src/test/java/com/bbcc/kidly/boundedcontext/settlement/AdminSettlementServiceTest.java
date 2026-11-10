package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.settlement.app.AdminSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementMetrics;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementAdminActions;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementPayoutRequester;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementAdminActionType;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSettlementServiceTest {

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long ADMIN_ID = 7L;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementPayoutRequester settlementPayoutRequester;

    @Mock
    private SettlementMetrics settlementMetrics;

    @Mock
    private SettlementAdminActions settlementAdminActions;

    @InjectMocks
    private AdminSettlementService adminSettlementService;

    @Test
    @DisplayName("관리자가 지급을 재요청하면 그 관리자 ID와 정산서 ID로 PAYOUT_RETRY를 남긴다")
    void retryPayoutRecordsAdmin() {
        given(settlementRepository.getById(SETTLEMENT_ID)).willReturn(SettlementFixture.settlement(10_000L));

        adminSettlementService.retryPayout(SETTLEMENT_ID, ADMIN_ID);

        verify(settlementAdminActions).record(eq(ADMIN_ID), eq(SettlementAdminActionType.PAYOUT_RETRY),
            eq(SETTLEMENT_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("재요청할 수 없는 정산서면 지급도 요청하지 않고 관리자 행위도 남기지 않는다")
    void retryPayoutRejectedRecordsNothing() {
        var paid = SettlementFixture.settlement(10_000L);
        paid.markPaid(LocalDateTime.now());
        given(settlementRepository.getById(SETTLEMENT_ID)).willReturn(paid);

        assertThatThrownBy(() -> adminSettlementService.retryPayout(SETTLEMENT_ID, ADMIN_ID))
            .isInstanceOf(BusinessException.class);

        verify(settlementPayoutRequester, never()).request(any(), any());
        verifyNoInteractions(settlementAdminActions);
    }

    @Test
    @DisplayName("자동 재요청은 관리자 행위가 아니라 남기지 않는다")
    void retryStuckPayoutRecordsNothing() {
        given(settlementRepository.getById(SETTLEMENT_ID)).willReturn(SettlementFixture.settlement(10_000L));

        adminSettlementService.retryStuckPayout(SETTLEMENT_ID);

        verifyNoInteractions(settlementAdminActions);
    }
}
