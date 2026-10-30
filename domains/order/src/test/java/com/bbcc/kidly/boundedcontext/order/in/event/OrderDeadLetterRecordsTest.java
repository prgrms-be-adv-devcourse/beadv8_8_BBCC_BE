package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class OrderDeadLetterRecordsTest {

    private static final String DLT = "kidly.payment.events.order.dlt";

    @Test
    @DisplayName("DLT 헤더에서 원래 토픽 · 파티션(int) · 오프셋(long) · 원인 예외를, 원래 헤더에서 eventType · eventId를 읽는다")
    void readsDeadLetterHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "kidly.payment.events".getBytes(UTF_8));
        headers.add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(Integer.BYTES).putInt(2).array());
        headers.add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(1234L).array());
        headers.add(KafkaHeaders.DLT_EXCEPTION_FQCN,
            "org.springframework.kafka.listener.ListenerExecutionFailedException".getBytes(UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN,
            "tools.jackson.databind.exc.InvalidFormatException".getBytes(UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Listener failed; bad enum".getBytes(UTF_8));
        headers.add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, "trace\n\tat a".getBytes(UTF_8));
        headers.add(OrderKafkaMessages.EVENT_TYPE, "payment.completed".getBytes(UTF_8));
        headers.add(OrderKafkaMessages.EVENT_ID, "e-1".getBytes(UTF_8));

        ReceiveDeadLetterCommand command = OrderDeadLetterRecords.command(record(headers, "42", "{\"a\":1}"));

        assertThat(command).isEqualTo(new ReceiveDeadLetterCommand("kidly.payment.events", 2, 1234L, "42",
            "payment.completed", "e-1", "{\"a\":1}", "tools.jackson.databind.exc.InvalidFormatException",
            "Listener failed; bad enum", "trace\n\tat a"));
    }

    @Test
    @DisplayName("원인 예외가 없으면 예외 종류는 바깥 예외, DLT 헤더가 없으면 DLT 자신의 위치 · 빈 예외로 읽는다(저장은 계속)")
    void fallsBackWithoutHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(KafkaHeaders.DLT_EXCEPTION_FQCN, "java.lang.IllegalStateException".getBytes(UTF_8));

        ReceiveDeadLetterCommand command = OrderDeadLetterRecords.command(record(headers, null, null));

        assertThat(command).isEqualTo(new ReceiveDeadLetterCommand(DLT, 0, 9L, null, null, null, null,
            "java.lang.IllegalStateException", null, null));
    }

    private static ConsumerRecord<String, String> record(RecordHeaders headers, String key, String value) {
        return new ConsumerRecord<>(DLT, 0, 9L, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
            ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, key, value, headers, Optional.empty());
    }
}
