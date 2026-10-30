package com.bbcc.kidly.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;

/**
 * Kafka 전송 실패 로그에 어떤 이벤트인지 찾을 값(토픽 · 키 · eventType · eventId)만 남긴다.
 * 메시지 본문(가입 이메일, 아이 키 · 몸무게 같은 개인정보)은 남기지 않는다.
 * Boot 기본 LoggingProducerListener는 키와 본문을 함께 남기거나(includeContents=true) 함께 빼서(false)
 * 이 둘을 나눌 수 없어 직접 구현한다.
 */
final class KafkaSendFailureLogger implements ProducerListener<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaSendFailureLogger.class);

    @Override
    public void onError(ProducerRecord<Object, Object> record, @Nullable RecordMetadata recordMetadata,
        Exception exception) {
        LOG.error("이벤트를 Kafka로 보내지 못했습니다. topic={} eventType={} eventId={} key={}",
            record.topic(), header(record, "eventType"), header(record, "eventId"), record.key(), exception);
    }

    private static @Nullable String header(ProducerRecord<Object, Object> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        byte[] value = header.value();
        if (value == null) {
            return null;
        }
        return new String(value, UTF_8);
    }
}
