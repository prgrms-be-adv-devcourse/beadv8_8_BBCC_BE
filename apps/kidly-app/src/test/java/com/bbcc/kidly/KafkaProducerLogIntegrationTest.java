package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.IntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.support.ProducerListener;

/**
 * Kafka 전송이 실패했을 때 남는 로그에 메시지 본문(가입 이메일, 아이 키 · 몸무게 등)이 들어가지 않는다.
 * 앱의 KafkaTemplate이 쓰는 ProducerListener가 실패를 기록하므로 그 빈으로 확인한다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class KafkaProducerLogIntegrationTest {

    private static final String TOPIC = "kidly.member.events";
    private static final String BODY_VALUE = "body-must-not-be-logged";

    @Autowired
    private ProducerListener<Object, Object> producerListener;

    @Test
    @DisplayName("Kafka 전송이 실패하면 로그에 토픽은 남기고 메시지 본문은 남기지 않는다")
    void sendFailureLogHasNoBody(CapturedOutput output) {
        ProducerRecord<Object, Object> record = new ProducerRecord<>(TOPIC, "15", "{\"data\":\"" + BODY_VALUE + "\"}");

        producerListener.onError(record, null, new IllegalStateException("broker unavailable"));

        assertThat(output.getOut()).contains(TOPIC).doesNotContain(BODY_VALUE);
    }
}
