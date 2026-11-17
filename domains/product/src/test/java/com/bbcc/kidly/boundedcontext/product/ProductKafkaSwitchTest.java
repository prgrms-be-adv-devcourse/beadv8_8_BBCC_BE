package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderKafkaListener;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.annotation.KafkaListener;

class ProductKafkaSwitchTest {

    @ParameterizedTest
    @ValueSource(classes = {ProductSellerKafkaListener.class, ProductOrderKafkaListener.class})
    @DisplayName("Kafka 리스너는 스위치(kidly.product.kafka.consumer.enabled)가 켜져 있을 때만 시작하고, 구독 그룹은 kidly.product다")
    void kafkaListenerStartsBySwitch(Class<?> listener) throws NoSuchMethodException {
        KafkaListener kafkaListener = listener.getMethod("on", ConsumerRecord.class).getAnnotation(KafkaListener.class);

        assertThat(kafkaListener.autoStartup()).isEqualTo("${kidly.product.kafka.consumer.enabled:true}");
        assertThat(kafkaListener.groupId()).isEqualTo("kidly.product");
        assertThat(kafkaListener.containerFactory()).isEqualTo("productKafkaListenerContainerFactory");
    }
}
