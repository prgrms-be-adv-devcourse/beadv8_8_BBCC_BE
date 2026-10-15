package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StockEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 14, 9, 0);

    @Mock
    private OrderService orderService;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("재고 차감 이벤트의 주문 ID로 결제 요청을 맡긴다")
    void requestsPayment() {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
            new StockDeductedEvent.Data(7L)));

        verify(orderService).requestPayment(7L);
    }

    @Test
    @DisplayName("주문 ID가 없으면 WARN을 남기고 결제 요청을 맡기지 않는다")
    void skipsWithoutOrderId(CapturedOutput output) {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
            new StockDeductedEvent.Data(null)));

        verifyNoInteractions(orderService);
        assertThat(output).containsPattern("WARN.*event=stock.deducted.*reason=missing-order-id");
    }

    @Test
    @DisplayName("데이터가 없으면 WARN을 남기고 결제 요청을 맡기지 않는다")
    void skipsWithoutData(CapturedOutput output) {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT, null));

        verifyNoInteractions(orderService);
        assertThat(output).containsPattern("WARN.*event=stock.deducted.*reason=missing-order-id");
    }
}
