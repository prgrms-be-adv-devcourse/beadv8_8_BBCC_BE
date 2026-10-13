package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Mock
    private ReplicaService replicaService;

    @InjectMocks
    private ProductEventListener listener;

    @Captor
    private ArgumentCaptor<ProductChangedCommand> command;

    @Test
    @DisplayName("이벤트를 커맨드로 바꿔 넘긴다: 상품 필드 · 옵션 전체 · 발생 시각")
    void convertsEvent() {
        listener.onProductChanged(event(ProductStatus.ON_SALE,
            new ProductChangedEvent.Option(1L, "110", "네이비", "면", 25_000L, 105, 115, false),
            new ProductChangedEvent.Option(2L, "120", "네이비", "면", 26_000L, 115, 125, true)));

        verify(replicaService).applyProductChanged(command.capture());
        ProductChangedCommand sent = command.getValue();
        assertThat(sent.productId()).isEqualTo(1L);
        assertThat(sent.sellerId()).isEqualTo(2L);
        assertThat(sent.productName()).isEqualTo("상품 A");
        assertThat(sent.categoryId()).isEqualTo(1L);
        assertThat(sent.productStatus()).isEqualTo("ON_SALE");
        assertThat(sent.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(sent.options()).hasSize(2);
        assertThat(sent.options().get(0)).isEqualTo(new ProductChangedCommand.Option(1L, "110", 25_000L, false));
        assertThat(sent.options().get(1)).isEqualTo(new ProductChangedCommand.Option(2L, "120", 26_000L, true));
    }

    @Test
    @DisplayName("판매 상태 enum은 이름 문자열로 넘긴다")
    void convertsStatusToName() {
        listener.onProductChanged(event(ProductStatus.STOPPED,
            new ProductChangedEvent.Option(1L, "110", "네이비", "면", 25_000L, null, null, false)));

        verify(replicaService).applyProductChanged(command.capture());
        assertThat(command.getValue().productStatus()).isEqualTo("STOPPED");
    }

    private static ProductChangedEvent event(ProductStatus status, ProductChangedEvent.Option... options) {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ProductChangedEvent.Data(
            1L, 2L, "상품 A", 1L, status, "https://example.com/a.png", OCCURRED_AT.minusDays(1), false,
            List.of(options)));
    }
}
