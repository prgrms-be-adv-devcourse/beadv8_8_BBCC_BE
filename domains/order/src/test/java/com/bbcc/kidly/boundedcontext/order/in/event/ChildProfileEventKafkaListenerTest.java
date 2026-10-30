package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.math.BigDecimal;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.childprofile.events 리스너(#541): child.changed만 자녀 복사본 처리에 넘기고 나머지 종류는 건너뛴다.
 */
@ExtendWith(MockitoExtension.class)
class ChildProfileEventKafkaListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Mock
    private ChildProfileEventListener childProfileEventListener;

    private ChildProfileEventKafkaListener listener() {
        return new ChildProfileEventKafkaListener(jsonMapper, childProfileEventListener);
    }

    @Test
    @DisplayName("child.changed는 본문을 읽어 자녀 복사본 처리에 넘긴다")
    void childChanged() {
        listener().on(record("child.changed", body("MALE")));

        var captor = ArgumentCaptor.forClass(ChildChangedEvent.class);
        verify(childProfileEventListener).onChildChanged(captor.capture());
        assertThat(captor.getValue().data().childId()).isEqualTo(3L);
        assertThat(captor.getValue().data().heightCm()).isEqualByComparingTo(new BigDecimal("115.5"));
        assertThat(captor.getValue().data().gender()).isEqualTo(Gender.MALE);
    }

    @Test
    @DisplayName("주문이 받지 않는 종류는 건너뛴다")
    void skipsOtherEventType() {
        listener().on(record("child.archived", "읽을 수 없는 본문"));

        verifyNoInteractions(childProfileEventListener);
    }

    @Test
    @DisplayName("모르는 성별 값은 읽기 실패로 던진다")
    void unknownEnumFails() {
        assertThatThrownBy(() -> listener().on(record("child.changed", body("OTHER"))))
            .isInstanceOf(JacksonException.class);

        verifyNoInteractions(childProfileEventListener);
    }

    private static String body(String gender) {
        return "{\"eventId\":\"11111111-2222-3333-4444-555555555555\",\"eventType\":\"child.changed\","
            + "\"schemaVersion\":1,\"occurredAt\":\"2026-10-30T09:00:00\",\"producer\":\"childprofile\","
            + "\"aggregateId\":\"3\",\"correlationId\":\"3\",\"data\":{\"childId\":3,\"memberId\":1,"
            + "\"heightCm\":115.5,\"weightKg\":20.0,\"gender\":\"" + gender + "\",\"birthDate\":\"2019-01-01\","
            + "\"bodyType\":\"NORMAL\",\"preferredFit\":\"LOOSE\",\"deleted\":false}}";
    }

    private static ConsumerRecord<String, String> record(String eventType, String body) {
        var record = new ConsumerRecord<>("kidly.childprofile.events", 0, 0L, "3", body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        return record;
    }
}
