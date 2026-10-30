package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxProperties;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductOutboxCleanupServiceTest {

    @Mock
    private ProductOutboxRepository outboxRepository;

    @Test
    @DisplayName("보관 기간(7일)보다 먼저 보낸 메시지를 지운다. 기준 시각은 실행한 때로부터 7일 전이다")
    void deleteMessagesSentBeforeRetention() {
        ProductOutboxCleanupService cleanupService = new ProductOutboxCleanupService(
            outboxRepository, new ProductOutboxProperties(100, Duration.ofDays(7)));
        LocalDateTime before = LocalDateTime.now();

        cleanupService.cleanUp();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).deleteSentBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(7), after.minusDays(7));
    }
}
