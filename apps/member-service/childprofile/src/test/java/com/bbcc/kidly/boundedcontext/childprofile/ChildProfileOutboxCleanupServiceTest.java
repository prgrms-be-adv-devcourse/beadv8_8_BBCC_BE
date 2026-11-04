package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildProfileOutboxCleanupServiceTest {

    @Mock
    private ChildProfileOutboxRepository outboxRepository;

    @Test
    @DisplayName("보관 기간(7일)보다 먼저 보낸 메시지를 지운다. 기준 시각은 실행한 때로부터 7일 전이다")
    void deleteMessagesSentBeforeRetention() {
        var cleanupService = new ChildProfileOutboxCleanupService(outboxRepository);
        var before = LocalDateTime.now();

        cleanupService.cleanUp();

        var after = LocalDateTime.now();
        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).deleteSentBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(7), after.minusDays(7));
    }
}
