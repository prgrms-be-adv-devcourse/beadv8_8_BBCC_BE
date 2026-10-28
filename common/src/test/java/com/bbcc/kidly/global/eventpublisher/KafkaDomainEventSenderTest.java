package com.bbcc.kidly.global.eventpublisher;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaDomainEventSenderTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaDomainEventSender sender;

    private final TestEvent event = new TestEvent(
        UUID.fromString("6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90"), LocalDateTime.of(2026, 10, 11, 0, 0),
        new TestEvent.Data(1L, 7L));

    @Test
    @DisplayName("토픽은 eventType, 키는 correlationId로 보낸다")
    void sendsToEventTypeTopicWithCorrelationIdKey() {
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());

        sender.send(event);

        verify(kafkaTemplate).send("test.happened", "7", event);
    }

    @Test
    @DisplayName("보내다 바로 실패해도 예외를 던지지 않는다 (이미 커밋된 업무를 실패로 만들지 않는다)")
    void swallowsImmediateFailure() {
        given(kafkaTemplate.send(anyString(), anyString(), any())).willThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> sender.send(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전송 결과가 실패여도 예외를 던지지 않는다")
    void swallowsAsyncFailure() {
        CompletableFuture<SendResult<String, Object>> failed =
            CompletableFuture.failedFuture(new IllegalStateException("timeout"));
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(failed);

        assertThatCode(() -> sender.send(event)).doesNotThrowAnyException();
    }

    private record TestEvent(UUID eventId, LocalDateTime occurredAt, Data data) implements DomainEvent {

        record Data(Long id, Long orderId) {
        }

        @Override
        public String aggregateId() {
            return String.valueOf(data.id());
        }

        @Override
        public String correlationId() {
            return String.valueOf(data.orderId());
        }

        @Override
        public String eventType() {
            return "test.happened";
        }
    }
}
