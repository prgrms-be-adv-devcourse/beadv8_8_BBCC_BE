package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderOutboxCleanupServiceTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Test
    @DisplayName("보관 기간(7일) 전보다 먼저 보낸 메시지를 지운다")
    void deletesSentBeforeRetention() {
        OrderOutboxCleanupService cleanupService = new OrderOutboxCleanupService(outboxRepository,
            new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7)));
        when(outboxRepository.deleteSentBefore(any())).thenReturn(3);
        LocalDateTime before = LocalDateTime.now().minusDays(7);

        int deleted = cleanupService.cleanUp();

        LocalDateTime after = LocalDateTime.now().minusDays(7);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).deleteSentBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
        assertThat(deleted).isEqualTo(3);
    }
}
