package com.bbcc.kidly.boundedcontext.recommend;

import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseRetentionService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiResponseRetentionServiceTest {

    // UTC 11-10 19:30은 한국 시간 11-11 04:30이다
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T19:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final Duration RETENTION = Duration.ofDays(30);

    @Mock
    private AiResponseStore store;

    @Test
    @DisplayName("지금부터 보관 기간 전보다 먼저 만든 AI 응답을 지운다")
    void deleteResponsesOlderThanRetention() {
        AiResponseRetentionService service = new AiResponseRetentionService(store, CLOCK, RETENTION);

        service.deleteExpiredResponses();

        verify(store).deleteCreatedBefore(LocalDateTime.of(2026, 10, 12, 4, 30));
    }
}
