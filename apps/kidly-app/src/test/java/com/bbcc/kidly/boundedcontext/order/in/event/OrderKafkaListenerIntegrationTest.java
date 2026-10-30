package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 주문 Kafka 수신 설정(#541)이 실제 앱 컨텍스트에서: DLT 토픽 3개를 선언하고, 스위치는 기본값(켜짐)이지만
 * 테스트 환경(spring.kafka.listener.auto-startup: false)에서는 주문 리스너 3개가 저절로 시작하지 않는다.
 */
@KafkaIntegrationTest
class OrderKafkaListenerIntegrationTest {

    private static final List<String> DEAD_LETTER_TOPICS = List.of(
        "kidly.product.events.order.dlt", "kidly.seller.events.order.dlt", "kidly.childprofile.events.order.dlt");

    private static final List<String> LISTENER_IDS = List.of(ProductEventKafkaListener.ID,
        SellerEventKafkaListener.ID, ChildProfileEventKafkaListener.ID);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private OrderKafkaConsumerProperties consumerProperties;

    @Autowired
    @Qualifier("orderKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<?, ?> orderFactory;

    @Test
    @DisplayName("기동하면 주문 DLT 토픽 3개가 파티션 1 · 복제 1로 생긴다")
    void deadLetterTopicsDeclared() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            Map<String, TopicDescription> topics = admin.describeTopics(DEAD_LETTER_TOPICS).allTopicNames()
                .get(10, TimeUnit.SECONDS);

            assertThat(topics).containsOnlyKeys(DEAD_LETTER_TOPICS);
            assertThat(topics.values()).allSatisfy(topic -> {
                assertThat(topic.partitions()).hasSize(1);
                assertThat(topic.partitions().getFirst().replicas()).hasSize(1);
            });
        }
    }

    // 스위치가 켜져 있으면 앱 값을 따른다. 테스트 yml이 앱 전체 auto-startup을 끄므로 다른 테스트 도중에 주문이 Kafka로 받지 않는다.
    // 스위치가 꺼져 있으면 앱 값과 상관없이 끄는 것은 OrderKafkaListenerConfigTest가 본다
    @Test
    @DisplayName("스위치는 켜져 있지만 테스트 환경에서는 주문 리스너 3개가 레지스트리에 있고 저절로 시작하지 않는다")
    void listenersRegisteredButNotStartedInTests() {
        assertThat(consumerProperties.enabled()).isTrue();
        assertThat(LISTENER_IDS).allSatisfy(id -> {
            MessageListenerContainer listener = registry.getListenerContainer(id);
            assertThat(listener).isNotNull();
            assertThat(listener.isAutoStartup()).isFalse();
            assertThat(listener.isRunning()).isFalse();
        });
        assertThat(orderFactory.createContainer("kidly.seller.events").isAutoStartup()).isFalse();
    }
}
