package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementSellerPlan;
import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementSellerSkipListener;
import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementSkipRecorder;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class SettlementSellerSkipListenerTest {

    private static final Long RUN_ID = 7L;

    @Mock
    private SettlementSkipRecorder settlementSkipRecorder;

    @Test
    @DisplayName("계산 · 저장 단계에서 건너뛴 판매자를 그 단계로 기록한다")
    void record() {
        var listener = new SettlementSellerSkipListener(settlementSkipRecorder, RUN_ID);
        var processError = new ArithmeticException();
        var writeError = new IllegalStateException();

        listener.onSkipInProcess(10L, processError);
        listener.onSkipInWrite(plan(11L), writeError);

        then(settlementSkipRecorder).should().record(RUN_ID, 10L, SettlementSkipPhase.PROCESS, processError);
        then(settlementSkipRecorder).should().record(RUN_ID, 11L, SettlementSkipPhase.WRITE, writeError);
    }

    @Test
    @DisplayName("기록에 실패해도 예외를 던지지 않아 정산이 멈추지 않는다")
    void recordFailureDoesNotStopSettlement() {
        var listener = new SettlementSellerSkipListener(settlementSkipRecorder, RUN_ID);
        willThrow(new DataAccessResourceFailureException("DB 연결 실패"))
            .given(settlementSkipRecorder).record(eq(RUN_ID), any(), any(), any());

        assertThatCode(() -> listener.onSkipInProcess(10L, new ArithmeticException())).doesNotThrowAnyException();
        assertThatCode(() -> listener.onSkipInWrite(plan(11L), new IllegalStateException()))
            .doesNotThrowAnyException();
    }

    private static SettlementSellerPlan plan(Long sellerId) {
        return new SettlementSellerPlan(sellerId, List.of(1L), 1_000L);
    }
}
