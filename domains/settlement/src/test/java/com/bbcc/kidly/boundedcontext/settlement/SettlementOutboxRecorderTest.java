package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.NOW;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_END;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_START;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRecorder;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementEventOutbox;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.settlement.event.SettlementPayoutRequestedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementOutboxRecorderTest {

    @Mock
    private SettlementEventOutbox settlementEventOutbox;

    @InjectMocks
    private SettlementOutboxRecorder settlementOutboxRecorder;

    @Test
    @DisplayName("정산이 발행한 이벤트는 아웃박스에 저장한다")
    void recordSettlementEvent() {
        SettlementPayoutRequestedEvent event = new SettlementPayoutRequestedEvent(UUID.randomUUID(), NOW,
            new SettlementPayoutRequestedEvent.Data(31L, SELLER_ID, 270_000L, PERIOD_START, PERIOD_END));

        settlementOutboxRecorder.record(event);

        then(settlementEventOutbox).should().append(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 저장하지 않는다")
    void skipOtherProducer() {
        PaymentPayoutCompletedEvent event = new PaymentPayoutCompletedEvent(UUID.randomUUID(), NOW,
            new PaymentPayoutCompletedEvent.Data(31L, SELLER_ID, 270_000L, 5L, NOW));

        settlementOutboxRecorder.record(event);

        then(settlementEventOutbox).should(never()).append(any());
    }
}
