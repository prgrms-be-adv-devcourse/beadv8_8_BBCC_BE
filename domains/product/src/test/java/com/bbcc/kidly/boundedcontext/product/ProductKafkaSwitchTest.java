package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryDeductionService;
import com.bbcc.kidly.boundedcontext.product.app.inventory.InventoryRestorationService;
import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderCancelledEventListener;
import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderEventListener;
import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderItemCancelledEventListener;
import com.bbcc.kidly.boundedcontext.product.in.inventory.ProductOrderKafkaListener;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerEventListener;
import com.bbcc.kidly.boundedcontext.product.in.replica.ProductSellerKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * 상품이 받는 다른 도메인 이벤트는 Kafka 리스너와 Spring 리스너 중 한쪽만 받아야 한다(#692).
 * Kafka 수신 스위치가 켜져 있으면(기본) Kafka 리스너만 받고, 꺼져 있을 때만 Spring 리스너가 만들어진다.
 */
class ProductKafkaSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ProductSellerEventListener.class, ProductOrderEventListener.class,
            ProductOrderCancelledEventListener.class, ProductOrderItemCancelledEventListener.class)
        .withBean(ProductReplicaSellerService.class, () -> mock(ProductReplicaSellerService.class))
        .withBean(InventoryDeductionService.class, () -> mock(InventoryDeductionService.class))
        .withBean(InventoryRestorationService.class, () -> mock(InventoryRestorationService.class));

    @ParameterizedTest
    @ValueSource(strings = {"kidly.product.kafka.consumer.enabled=true", "kidly.product.other=true"})
    @DisplayName("Kafka 수신 스위치가 켜져 있거나 지정되지 않으면 판매자 · 주문 · 판매자 부분 취소 이벤트의 Spring 리스너를 만들지 않는다")
    void notRegisteredWhenKafkaReceives(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context)
            .doesNotHaveBean(ProductSellerEventListener.class)
            .doesNotHaveBean(ProductOrderEventListener.class)
            .doesNotHaveBean(ProductOrderCancelledEventListener.class)
            .doesNotHaveBean(ProductOrderItemCancelledEventListener.class));
    }

    @Test
    @DisplayName("Kafka 수신 스위치를 끄면 판매자 · 주문 · 판매자 부분 취소 이벤트의 Spring 리스너가 만들어진다")
    void registeredWhenKafkaIsOff() {
        runner.withPropertyValues("kidly.product.kafka.consumer.enabled=false").run(context -> assertThat(context)
            .hasSingleBean(ProductSellerEventListener.class)
            .hasSingleBean(ProductOrderEventListener.class)
            .hasSingleBean(ProductOrderCancelledEventListener.class)
            .hasSingleBean(ProductOrderItemCancelledEventListener.class));
    }

    // 테스트 설정은 리스너가 저절로 시작하지 않게 막아 두므로, 스위치를 따르는지는 애노테이션 값으로 확인한다
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
