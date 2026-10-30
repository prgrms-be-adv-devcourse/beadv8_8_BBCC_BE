package com.bbcc.kidly.boundedcontext.member;

import static com.bbcc.kidly.boundedcontext.member.MemberFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.member.domain.outbox.MemberOutboxMessage;
import com.bbcc.kidly.boundedcontext.member.out.outbox.JpaMemberEventOutbox;
import com.bbcc.kidly.boundedcontext.member.out.outbox.JpaMemberOutboxRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
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
class JpaMemberEventOutboxTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final MemberWithdrawnEvent WITHDRAWN =
        new MemberWithdrawnEvent(EVENT_ID, NOW, new MemberWithdrawnEvent.Data(1L));

    @Mock
    private JpaMemberOutboxRepository outboxRepository;

    @Test
    @DisplayName("이벤트를 kidly.member.events로 보낼 메시지로 저장한다. 키는 correlationId(회원 ID), 종류와 ID는 이벤트 값이다")
    void appendMessageWithTopicKeyAndType() {
        var saved = appendAndCapture();

        assertThat(saved)
            .extracting(
                MemberOutboxMessage::getTopic,
                MemberOutboxMessage::getMessageKey,
                MemberOutboxMessage::getEventType,
                MemberOutboxMessage::getEventId)
            .containsExactly("kidly.member.events", "1", "member.withdrawn", EVENT_ID);
    }

    @Test
    @DisplayName("본문은 공통 칸 7개 다음에 data를 둔 JSON이다. 회원 ID는 숫자, 시각은 시간대 없는 문자열이다")
    void appendBodyWithCommonFieldsAndData() {
        JsonNode body = JSON_MAPPER.readTree(appendAndCapture().getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(body.get("eventType").asString()).isEqualTo("member.withdrawn");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-12T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("member");
        assertThat(body.get("aggregateId").asString()).isEqualTo("1");
        assertThat(body.get("correlationId").asString()).isEqualTo("1");
        assertThat(body.get("data").get("memberId").isIntegralNumber()).isTrue();
        assertThat(body.get("data").get("memberId").asLong()).isEqualTo(1L);
    }

    private MemberOutboxMessage appendAndCapture() {
        new JpaMemberEventOutbox(outboxRepository, JSON_MAPPER).append(WITHDRAWN);
        var captor = ArgumentCaptor.forClass(MemberOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
