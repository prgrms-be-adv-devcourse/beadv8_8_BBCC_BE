package com.bbcc.kidly.boundedcontext.order.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

/**
 * 주문 전용 Producer 설정(#540). Boot가 만든 ProducerFactory(주소 · acks=all · 멱등 등 앱 spring.kafka)를
 * 직렬화 · 대기 시간만 덮어 복사해 쓰고, 보내기 한 번이 기다리는 시간을 묶는다. 실제 전송은 Kafka 통합 테스트에서 확인한다.
 */
class KafkaOrderEventSenderTest {

    @Test
    @DisplayName("Boot ProducerFactory를 문자열 직렬화 · 대기 5초 · 요청 5초 · 전달 10초로만 덮어 복사한다(acks · 멱등은 앱 설정 그대로)")
    void copiesBootFactoryWithTimeoutsOnly() {
        @SuppressWarnings("unchecked")
        ProducerFactory<String, String> bootFactory = mock(ProducerFactory.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> overrides = ArgumentCaptor.forClass(Map.class);
        when(bootFactory.copyWithConfigurationOverride(any())).thenAnswer(invocation -> mock(ProducerFactory.class));

        new KafkaOrderEventSender(bootFactory);

        verify(bootFactory).copyWithConfigurationOverride(overrides.capture());
        assertThat(overrides.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000));
    }

    @Test
    @DisplayName("한 번 보내기를 기다리는 상한은 메타데이터 대기 5초 + 전달 10초 = 15초")
    void sendTimeoutCoversBlockAndDelivery() {
        assertThat(KafkaOrderEventSender.SEND_TIMEOUT).hasSeconds(15);
    }

    @Test
    @DisplayName("앱이 끝날 때 복사한 팩토리를 닫는다(Producer 종료). Boot 빈은 Boot가 닫는다")
    void destroysCopiedFactory() {
        @SuppressWarnings("unchecked")
        ProducerFactory<String, String> bootFactory = mock(ProducerFactory.class);
        DefaultKafkaProducerFactory<?, ?> copied = mock(DefaultKafkaProducerFactory.class);
        when(bootFactory.copyWithConfigurationOverride(any())).thenAnswer(invocation -> copied);

        new KafkaOrderEventSender(bootFactory).destroy();

        verify(copied).destroy();
    }
}
