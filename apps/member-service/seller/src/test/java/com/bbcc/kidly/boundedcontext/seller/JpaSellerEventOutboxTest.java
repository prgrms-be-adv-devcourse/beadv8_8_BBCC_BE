package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.out.outbox.JpaSellerEventOutbox;
import com.bbcc.kidly.boundedcontext.seller.out.outbox.JpaSellerOutboxRepository;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JpaSellerEventOutboxTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");
    private static final SellerChangedEvent CHANGED =
        new SellerChangedEvent(EVENT_ID, NOW, new SellerChangedEvent.Data(1L, "상호", SellerStatus.ACTIVE));

    @Mock
    private JpaSellerOutboxRepository outboxRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("이벤트를 kidly.seller.events로 보낼 메시지로 저장한다. 키는 correlationId(판매자 ID), 종류와 ID는 이벤트 값이다")
    void appendMessageWithTopicKeyAndType() {
        var saved = appendAndCapture();

        assertThat(saved)
            .extracting(
                SellerOutboxMessage::getTopic,
                SellerOutboxMessage::getMessageKey,
                SellerOutboxMessage::getEventType,
                SellerOutboxMessage::getEventId)
            .containsExactly("kidly.seller.events", "1", "seller.changed", EVENT_ID);
    }

    @Test
    @DisplayName("본문은 공통 칸 7개 다음에 data를 둔 JSON이다. 판매자 ID는 숫자, 시각은 시간대 없는 문자열이다")
    void appendBodyWithCommonFieldsAndData() {
        JsonNode body = JSON_MAPPER.readTree(appendAndCapture().getPayload());

        assertThat(body.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(body.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(body.get("eventType").asString()).isEqualTo("seller.changed");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(body.get("occurredAt").asString()).isEqualTo("2026-10-13T09:00:00");
        assertThat(body.get("producer").asString()).isEqualTo("seller");
        assertThat(body.get("aggregateId").asString()).isEqualTo("1");
        assertThat(body.get("correlationId").asString()).isEqualTo("1");
        assertThat(body.get("data").get("sellerId").isIntegralNumber()).isTrue();
        assertThat(body.get("data").get("sellerId").asLong()).isEqualTo(1L);
    }

    private SellerOutboxMessage appendAndCapture() {
        new JpaSellerEventOutbox(outboxRepository, JSON_MAPPER, eventPublisher).append(CHANGED);
        var captor = ArgumentCaptor.forClass(SellerOutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
