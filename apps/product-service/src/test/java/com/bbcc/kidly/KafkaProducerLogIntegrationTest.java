package com.bbcc.kidly;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.IntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.support.ProducerListener;

/**
 * Boot 기본 템플릿은 이 서비스의 ProducerListener 빈을 쓴다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class KafkaProducerLogIntegrationTest {

    @Autowired
    private ProducerListener<Object, Object> producerListener;

    @Test
    @DisplayName("Kafka 전송 실패 로그에는 토픽 · eventType · eventId · 키만 남기고 본문은 남기지 않는다")
    void sendFailureLogWithoutBody(CapturedOutput output) {
        var record = new ProducerRecord<Object, Object>("kidly.seller.events.product.dlt", "11",
            "{\"data\":{\"businessName\":\"소소한옷장\"}}");
        record.headers().add("eventType", "seller.changed".getBytes(UTF_8));
        record.headers().add("eventId", "6f1c2b9e-0d5a-4f0e-9a51-2f3c4d5e6f70".getBytes(UTF_8));

        producerListener.onError(record, null, new TimeoutException("전송 대기 시간이 지났습니다"));

        assertThat(output.getOut() + output.getErr())
            .contains("topic=kidly.seller.events.product.dlt")
            .contains("eventType=seller.changed")
            .contains("eventId=6f1c2b9e-0d5a-4f0e-9a51-2f3c4d5e6f70")
            .contains("key=11")
            .doesNotContain("소소한옷장");
    }
}
