package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.UnreadableOrderMessageException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 받은 Kafka 메시지의 헤더 읽기(#541). eventType 헤더가 없으면 읽기 실패(재시도 없이 DLT로 가는 예외)이고 메시지에 본문이 없다.
 */
class OrderKafkaMessagesTest {

    private static final String BODY = "{\"address\":\"본문-표식-주소\"}";

    @Test
    @DisplayName("헤더 값을 UTF-8 문자열로 읽고, 없으면 null")
    void header() {
        var record = record();
        record.headers().add("eventId", "evt-1".getBytes(UTF_8));

        assertThat(OrderKafkaMessages.header(record, "eventId")).isEqualTo("evt-1");
        assertThat(OrderKafkaMessages.header(record, "eventType")).isNull();
    }

    @Test
    @DisplayName("eventType 헤더를 돌려준다")
    void eventType() {
        var record = record();
        record.headers().add("eventType", "seller.changed".getBytes(UTF_8));

        assertThat(OrderKafkaMessages.eventType(record)).isEqualTo("seller.changed");
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 읽기 실패 전용 예외(IllegalArgumentException이라 재시도 없음 · DLT에서 READ_FAILED), "
        + "메시지에 위치는 있고 본문은 없다")
    void missingEventType() {
        assertThatThrownBy(() -> OrderKafkaMessages.eventType(record()))
            .isInstanceOf(UnreadableOrderMessageException.class)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kidly.seller.events-1@5")
            .hasMessageNotContaining("본문-표식-주소");
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("kidly.seller.events", 1, 5L, "7", BODY);
    }
}
