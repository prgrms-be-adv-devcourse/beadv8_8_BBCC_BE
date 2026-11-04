package com.bbcc.kidly.boundedcontext.childprofile;

import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.out.outbox.JpaChildProfileEventOutbox;
import com.bbcc.kidly.boundedcontext.childprofile.out.outbox.JpaChildProfileOutboxRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JpaChildProfileEventOutboxTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final Long CHILD_ID = 3L;
    private static final Long MEMBER_ID = 1L;
    private static final ChildChangedEvent DELETED = new ChildChangedEvent(EVENT_ID, NOW, new ChildChangedEvent.Data(
        CHILD_ID, MEMBER_ID, ChildProfileFixture.HEIGHT, ChildProfileFixture.WEIGHT, Gender.MALE,
        ChildProfileFixture.BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, true));

    @Mock
    private JpaChildProfileOutboxRepository outboxRepository;

    @Test
    @DisplayName("이벤트를 kidly.childprofile.events로 보낼 메시지로 저장한다. 키는 correlationId(자녀 ID), 종류와 ID는 이벤트 값이다")
    void appendMessageWithTopicKeyAndType() {
        var saved = appendAndCapture();

        assertThat(saved)
            .extracting(
                ChildProfileOutboxMessage::getTopic,
                ChildProfileOutboxMessage::getMessageKey,
                ChildProfileOutboxMessage::getEventType,
                ChildProfileOutboxMessage::getEventId)
            .containsExactly("kidly.childprofile.events", "3", "child.changed", EVENT_ID);
    }

    @Test
    @DisplayName("본문은 공통 칸 7개 다음에 data를 둔 JSON이다. ID · 키는 숫자, 시각 · 생년월일은 시간대 없는 문자열이다")
    void appendBodyWithCommonFieldsAndData() {
        JsonNode body = JSON_MAPPER.readTree(appendAndCapture().getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(body.get("eventType").asString()).isEqualTo("child.changed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-13T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("childprofile");
        assertThat(body.get("aggregateId").asString()).isEqualTo("3");
        assertThat(body.get("correlationId").asString()).isEqualTo("3");
        assertThat(body.get("data").get("childId").isIntegralNumber()).isTrue();
        assertThat(body.get("data").get("childId").asLong()).isEqualTo(CHILD_ID);
        assertThat(body.get("data").get("heightCm").isNumber()).isTrue();
        assertThat(body.get("data").get("heightCm").asDouble()).isEqualTo(115.0);
        assertThat(body.get("data").get("birthDate").asString()).isEqualTo("2019-01-15");
        assertThat(body.get("data").get("deleted").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("같은 eventId가 이미 저장돼 있으면 메시지를 하나 더 만들지 않는다")
    void skipSameEventId() {
        given(outboxRepository.existsByEventId(EVENT_ID)).willReturn(true);

        new JpaChildProfileEventOutbox(outboxRepository, JSON_MAPPER).append(DELETED);

        then(outboxRepository).should(never()).save(any());
    }

    private ChildProfileOutboxMessage appendAndCapture() {
        new JpaChildProfileEventOutbox(outboxRepository, JSON_MAPPER).append(DELETED);
        var captor = ArgumentCaptor.forClass(ChildProfileOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
