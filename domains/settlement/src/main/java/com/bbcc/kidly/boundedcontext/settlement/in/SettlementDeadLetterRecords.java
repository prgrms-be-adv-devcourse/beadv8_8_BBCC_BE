package com.bbcc.kidly.boundedcontext.settlement.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Received;
import java.nio.ByteBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * DLT 메시지를 보관 행으로 바꾼다. 원래 위치 · 예외 종류는 DeadLetterPublishingRecoverer가 붙인 헤더에서 읽는다.
 */
final class SettlementDeadLetterRecords {

    static final String EVENT_TYPE = "eventType";
    static final String EVENT_ID = "eventId";

    private SettlementDeadLetterRecords() {
    }

    static Received from(ConsumerRecord<String, String> record) {
        String source = header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        if (source == null) {
            String suffix = SettlementDeadLetterKafkaListenerConfig.DLT_SUFFIX;
            source = record.topic().endsWith(suffix)
                ? record.topic().substring(0, record.topic().length() - suffix.length()) : record.topic();
        }
        String cause = header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        return new Received(record.topic(), record.partition(), record.offset(), clip(source, 100),
            partition(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)),
            offset(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)), clip(record.key(), 100),
            clip(header(record, EVENT_TYPE), 100), clip(header(record, EVENT_ID), 100), record.value(),
            clip(cause == null ? header(record, KafkaHeaders.DLT_EXCEPTION_FQCN) : cause, 255));
    }

    static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), UTF_8);
    }

    private static Integer partition(Header h) {
        if (h == null || h.value() == null || h.value().length != Integer.BYTES) {
            return null;
        }
        int value = ByteBuffer.wrap(h.value()).getInt();
        return value < 0 ? null : value;
    }

    private static Long offset(Header h) {
        if (h == null || h.value() == null || h.value().length != Long.BYTES) {
            return null;
        }
        long value = ByteBuffer.wrap(h.value()).getLong();
        return value < 0 ? null : value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.replace((char) 0, '�').replace('\r', ' ').replace('\n', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
