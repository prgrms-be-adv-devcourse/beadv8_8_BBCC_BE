package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Received;
import java.nio.ByteBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;

final class CommunityDeadLetterRecords {
    private CommunityDeadLetterRecords() {
    }

    static Received from(ConsumerRecord<String, String> record) {
        String source = CommunityKafkaMessages.header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        if (source == null) {
            String suffix = ".community.dlt";
            source = record.topic().endsWith(suffix)
                ? record.topic().substring(0, record.topic().length() - suffix.length()) : record.topic();
        }
        String cause = CommunityKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        return new Received(record.topic(), record.partition(), record.offset(), clip(source, 100),
            partition(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION)),
            offset(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)), clip(record.key(), 100),
            clip(CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_TYPE), 100),
            clip(CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_ID), 100), record.value(),
            clip(cause == null ? CommunityKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_FQCN) : cause, 255));
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
        String text = value.replace((char) 0, '\uFFFD').replace('\r', ' ').replace('\n', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
