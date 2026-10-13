package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.mockito.Mockito.verify;

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

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("재고 차감 이벤트의 주문 ID로 결제 요청을 맡긴다")
    void requestsPayment() {
        listener.onStockDeducted(new StockDeductedEvent(UUID.randomUUID(), LocalDateTime.of(2026, 10, 14, 9, 0),
            new StockDeductedEvent.Data(7L)));

        verify(orderService).requestPayment(7L);
    }
}
