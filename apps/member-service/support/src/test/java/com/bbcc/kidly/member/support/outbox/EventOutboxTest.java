package com.bbcc.kidly.member.support.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EventOutboxTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final MemberWithdrawnEvent WITHDRAWN =
        new MemberWithdrawnEvent(EVENT_ID, OCCURRED_AT, new MemberWithdrawnEvent.Data(1L));

    @Mock
    private OutboxRepository<TestOutboxMessage> repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("이벤트를 토픽으로 보낼 메시지로 저장하고, 저장한 메시지를 알린다. 키는 correlationId, 종류와 ID는 이벤트 값이다")
    void appendSavesMessageAndPublishesIt() {
        given(repository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        outbox(envelope -> "{}").append(WITHDRAWN);

        var saved = ArgumentCaptor.forClass(TestOutboxMessage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue())
            .extracting(OutboxMessage::getTopic, OutboxMessage::getMessageKey, OutboxMessage::getEventType,
                OutboxMessage::getEventId, OutboxMessage::getPayload)
            .containsExactly("kidly.member.events", "1", "member.withdrawn", EVENT_ID, "{}");
        verify(eventPublisher).publishEvent(saved.getValue());
    }

    @Test
    @DisplayName("본문은 공통 칸 7개와 data를 이벤트 값으로 채운 모양이다")
    void appendSerializesEnvelopeOfEvent() {
        var serialized = new AtomicReference<EventEnvelope>();

        outbox(envelope -> {
            serialized.set(envelope);
            return "{}";
        }).append(WITHDRAWN);

        assertThat(serialized.get()).isEqualTo(new EventEnvelope(EVENT_ID, "member.withdrawn", 1, OCCURRED_AT,
            "member", "1", "1", new MemberWithdrawnEvent.Data(1L)));
    }

    private EventOutbox<TestOutboxMessage> outbox(Function<EventEnvelope, String> toJson) {
        return new EventOutbox<>(repository, TestOutboxMessage::new, "kidly.member.events", toJson, eventPublisher) {
        };
    }
}
