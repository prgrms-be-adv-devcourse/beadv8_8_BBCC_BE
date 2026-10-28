package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.SellerChangedCommand;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SellerReplicaEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 9, 0);

    @Mock
    private ReplicaService replicaService;

    @InjectMocks
    private SellerReplicaEventListener listener;

    @Test
    @DisplayName("이벤트를 커맨드로 바꿔 넘긴다: 상태는 enum 이름 문자열")
    void convertsEvent() {
        listener.onSellerChanged(new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(2L, "키들리샵", SellerStatus.SUSPENDED)));

        verify(replicaService).applySellerChanged(new SellerChangedCommand(2L, "키들리샵", "SUSPENDED", OCCURRED_AT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventsMissingRequiredValue")
    @DisplayName("데이터 · 판매자 ID · 발생 시각 · 상호명 · 상태가 없으면 무시하고(기존 복사본 안 덮음) WARN 한 줄")
    void skipsEventMissingRequiredValue(String reason, SellerChangedEvent event, CapturedOutput output) {
        listener.onSellerChanged(event);

        verifyNoInteractions(replicaService);
        assertThat(output).containsPattern("WARN.*event=seller.changed sellerId=.* reason=" + reason);
        assertThat(output.getAll().lines().filter(line -> line.contains("event=seller.changed")).count())
            .isEqualTo(1);
    }

    static Stream<Arguments> eventsMissingRequiredValue() {
        return Stream.of(
            Arguments.of("missing-data", new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT, null)),
            Arguments.of("missing-seller-id", new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new SellerChangedEvent.Data(null, "키들리샵", SellerStatus.ACTIVE))),
            Arguments.of("missing-occurred-at", new SellerChangedEvent(UUID.randomUUID(), null,
                new SellerChangedEvent.Data(2L, "키들리샵", SellerStatus.ACTIVE))),
            Arguments.of("missing-business-name", new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new SellerChangedEvent.Data(2L, null, SellerStatus.ACTIVE))),
            Arguments.of("missing-status", new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new SellerChangedEvent.Data(2L, "키들리샵", null))));
    }
}
