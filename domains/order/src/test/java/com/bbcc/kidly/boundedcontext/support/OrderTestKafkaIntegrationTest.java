package com.bbcc.kidly.boundedcontext.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 주문 Kafka 테스트 기반(#616): Kafka 컨테이너가 뜨고, 주문이 보내고 받는 토픽 · 주문 DLT가 생긴다.
 */
@KafkaIntegrationTest
class OrderTestKafkaIntegrationTest {

    @Autowired
    private KafkaContainer kafka;

    @Test
    @DisplayName("주문 토픽 · 받는 토픽 6개 · 주문 DLT 7개가 생긴다(자녀 계정 kidly.auth.events #906, 자기 토픽 DLT #999, "
        + "회원 kidly.member.events #1001)")
    void topicsDeclared() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);

            assertThat(topics).contains("kidly.order.events", "kidly.product.events", "kidly.seller.events",
                "kidly.childprofile.events", "kidly.payment.events", "kidly.product.events.order.dlt",
                "kidly.seller.events.order.dlt", "kidly.childprofile.events.order.dlt",
                "kidly.payment.events.order.dlt", "kidly.auth.events", "kidly.auth.events.order.dlt",
                "kidly.order.events.order.dlt", "kidly.member.events", "kidly.member.events.order.dlt");
        }
    }
}
