package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.settlement.app.support.SettlementSkipRecorder;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkip;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipPhase;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementSkipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementSkipRecorderTest {

    @Mock
    private SettlementSkipRepository settlementSkipRepository;

    @InjectMocks
    private SettlementSkipRecorder settlementSkipRecorder;

    @Test
    @DisplayName("사유는 예외 이름: 메시지로 남긴다")
    void reasonWithMessage() {
        settlementSkipRecorder.record(
            7L, 10L, SettlementSkipPhase.WRITE, new IllegalStateException("정산 대상이 바뀌었습니다"));

        assertThat(saved().getReason()).isEqualTo("IllegalStateException: 정산 대상이 바뀌었습니다");
    }

    @Test
    @DisplayName("메시지가 없으면 예외 이름만 남긴다")
    void reasonWithoutMessage() {
        settlementSkipRecorder.record(7L, 10L, SettlementSkipPhase.PROCESS, new ArithmeticException());

        var saved = saved();
        assertThat(saved.getReason()).isEqualTo("ArithmeticException");
        assertThat(saved.getPhase()).isEqualTo(SettlementSkipPhase.PROCESS);
    }

    private SettlementSkip saved() {
        var captor = ArgumentCaptor.forClass(SettlementSkip.class);
        then(settlementSkipRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
