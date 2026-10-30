package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderKafkaConsumerProperties;
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
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 주문 Kafka 수신 설정(#541)이 실제 앱 컨텍스트에서: DLT 토픽 4개를 선언한다. 테스트 설정은 스위치를 끄므로(application-test.yml)
 * 주문은 Spring 이벤트로 받고(다른 도메인 테스트가 지금처럼 Spring 경로로 돈다), 주문 Kafka 리스너 4개는 자동 시작하지 않는다.
 */
@KafkaIntegrationTest
class OrderKafkaListenerIntegrationTest {

    private static final List<String> DEAD_LETTER_TOPICS = List.of(
        "kidly.product.events.order.dlt", "kidly.seller.events.order.dlt", "kidly.childprofile.events.order.dlt",
        "kidly.payment.events.order.dlt");

    private static final List<String> LISTENER_IDS = List.of(ProductEventKafkaListener.ID,
        SellerEventKafkaListener.ID, ChildProfileEventKafkaListener.ID, PaymentEventKafkaListener.ID);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private OrderKafkaListenerTestGuard guard;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private OrderKafkaConsumerProperties consumerProperties;

    @Autowired
    @Qualifier("orderKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<?, ?> orderFactory;

    @Test
    @DisplayName("기동하면 주문 DLT 토픽 4개가 파티션 1 · 복제 1로 생긴다")
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

    // 켜진 스위치(기본값)에서 Spring 수신이 없는 것은 SpringEventReceiverTest가 본다.
    // 공용 테스트 컨텍스트가 일시 정지 뒤 다시 시작될 때 레지스트리가 모든 컨테이너를 시작하는 문제(#541-2) 때문에
    // 주문 리스너 4개는 OrderKafkaListenerTestGuard가 레지스트리에서 빼 들고 있다
    @Test
    @DisplayName("테스트 설정은 스위치가 꺼져 있어 Spring 수신이 있고, 주문 Kafka 리스너 4개는 테스트가 켤 때까지 멈춰 있다")
    void springReceiverInTestsAndListenersStopped() {
        assertThat(consumerProperties.enabled()).isFalse();
        assertThat(context.getBeansOfType(SpringEventReceiver.class)).hasSize(1);
        assertThat(registry.getListenerContainerIds()).doesNotContainAnyElementsOf(LISTENER_IDS);
        assertThat(LISTENER_IDS).allSatisfy(id -> {
            MessageListenerContainer listener = guard.listener(id);
            assertThat(listener.isAutoStartup()).isFalse();
            assertThat(listener.isRunning()).isFalse();
        });
        assertThat(orderFactory.createContainer("kidly.seller.events").isAutoStartup()).isFalse();
    }
}
