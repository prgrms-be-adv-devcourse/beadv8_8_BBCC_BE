package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * 받은 Kafka 메시지의 헤더를 읽는다(#541). 예외 메시지에는 본문 대신 위치(토픽-파티션@오프셋)만 남긴다.
 */
final class OrderKafkaMessages {

    static final String EVENT_TYPE = "eventType";
    static final String EVENT_ID = "eventId";

    private OrderKafkaMessages() {
    }

    /**
     * @return 헤더 값(UTF-8). 없으면 null
     */
    static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), UTF_8);
    }

    /**
     * 종류를 고를 eventType 헤더. 없으면 읽지 못한 메시지라 IllegalArgumentException(재시도 없이 DLT)을 던진다.
     */
    static String eventType(ConsumerRecord<?, ?> record) {
        String eventType = header(record, EVENT_TYPE);
        if (eventType == null) {
            throw new IllegalArgumentException("eventType 헤더 없음: " + record.topic() + "-" + record.partition()
                + "@" + record.offset());
        }
        return eventType;
    }
}
