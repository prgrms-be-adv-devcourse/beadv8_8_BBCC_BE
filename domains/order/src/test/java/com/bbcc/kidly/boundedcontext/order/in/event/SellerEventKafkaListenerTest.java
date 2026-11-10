package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.order.app.order.SellerUnavailableCleanupService;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events 리스너(#541): seller.changed만 판매자 복사본 처리에 넘기고 나머지 종류는 건너뛴다. 복사본 반영 뒤 판매 불가
 * 판매자 정리를 부른다(#1121).
 */
@ExtendWith(MockitoExtension.class)
class SellerEventKafkaListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Mock
    private SellerReplicaEventListener sellerReplicaEventListener;

    @Mock
    private SellerUnavailableCleanupService cleanupService;

    private SellerEventKafkaListener listener() {
        return new SellerEventKafkaListener(jsonMapper, sellerReplicaEventListener, cleanupService);
    }

    @Test
    @DisplayName("seller.changed는 본문을 읽어 판매자 복사본 처리에 넘기고, 그 뒤 판매 불가 판매자 정리를 부른다(#1121)")
    void sellerChanged() {
        listener().on(record("seller.changed", body("ACTIVE")));

        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        InOrder order = inOrder(sellerReplicaEventListener, cleanupService);
        order.verify(sellerReplicaEventListener).onSellerChanged(captor.capture());
        order.verify(cleanupService).cleanUpIfUnavailable(2L);
        assertThat(captor.getValue().data()).isEqualTo(new SellerChangedEvent.Data(2L, "키들리샵", SellerStatus.ACTIVE));
    }

    @Test
    @DisplayName("주문이 받지 않는 종류는 건너뛴다")
    void skipsOtherEventType() {
        listener().on(record("seller.deleted", "읽을 수 없는 본문"));

        verifyNoInteractions(sellerReplicaEventListener, cleanupService);
    }

    @Test
    @DisplayName("모르는 판매자 상태는 읽기 실패로 던진다")
    void unknownEnumFails() {
        assertThatThrownBy(() -> listener().on(record("seller.changed", body("NEW_STATUS"))))
            .isInstanceOf(JacksonException.class);

        verifyNoInteractions(sellerReplicaEventListener, cleanupService);
    }

    private static String body(String status) {
        return "{\"eventId\":\"11111111-2222-3333-4444-555555555555\",\"eventType\":\"seller.changed\","
            + "\"schemaVersion\":1,\"occurredAt\":\"2026-10-30T09:00:00\",\"producer\":\"seller\","
            + "\"aggregateId\":\"2\",\"correlationId\":\"2\","
            + "\"data\":{\"sellerId\":2,\"businessName\":\"키들리샵\",\"status\":\"" + status + "\"}}";
    }

    private static ConsumerRecord<String, String> record(String eventType, String body) {
        var record = new ConsumerRecord<>("kidly.seller.events", 0, 0L, "2", body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        return record;
    }
}
