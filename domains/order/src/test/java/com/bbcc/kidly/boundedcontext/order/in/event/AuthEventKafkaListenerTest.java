package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.auth.events 리스너(#906): child-account.changed만 자녀 계정 복사본 처리에 넘기고 토큰 무효화 등 나머지 종류는 건너뛴다.
 */
@ExtendWith(MockitoExtension.class)
class AuthEventKafkaListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Mock
    private ChildAccountEventListener childAccountEventListener;

    private AuthEventKafkaListener listener() {
        return new AuthEventKafkaListener(jsonMapper, childAccountEventListener);
    }

    @Test
    @DisplayName("child-account.changed는 본문을 읽어 자녀 계정 복사본 처리에 넘긴다")
    void childAccountChanged() {
        listener().on(record("child-account.changed", body()));

        var captor = ArgumentCaptor.forClass(ChildAccountChangedEvent.class);
        verify(childAccountEventListener).onChildAccountChanged(captor.capture());
        assertThat(captor.getValue().data())
            .isEqualTo(new ChildAccountChangedEvent.Data(10L, 3L, 1L, false));
    }

    @Test
    @DisplayName("토큰 무효화(auth.tokens-revoked) 등 주문이 받지 않는 종류는 본문을 읽지 않고 건너뛴다")
    void skipsOtherEventType() {
        listener().on(record("auth.tokens-revoked", "읽을 수 없는 본문"));

        verifyNoInteractions(childAccountEventListener);
    }

    private static String body() {
        return "{\"eventId\":\"11111111-2222-3333-4444-555555555555\",\"eventType\":\"child-account.changed\","
            + "\"schemaVersion\":1,\"occurredAt\":\"2026-11-05T21:00:00\",\"producer\":\"auth\","
            + "\"aggregateId\":\"10\",\"correlationId\":\"10\",\"data\":{\"accountId\":10,\"childProfileId\":3,"
            + "\"guardianId\":1,\"deleted\":false,\"newField\":\"무시\"}}";
    }

    private static ConsumerRecord<String, String> record(String eventType, String body) {
        var record = new ConsumerRecord<>("kidly.auth.events", 0, 0L, "10", body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        return record;
    }
}
