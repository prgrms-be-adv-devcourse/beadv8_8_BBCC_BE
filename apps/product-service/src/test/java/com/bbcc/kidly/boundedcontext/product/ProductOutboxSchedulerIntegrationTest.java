package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.in.outbox.ProductOutboxScheduler;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 일반 통합 테스트는 Kafka 없이 돈다(kidly.kafka.producer.enabled=false). 이때 상품 아웃박스 스케줄러는 만들지 않는다.
 */
@IntegrationTest
class ProductOutboxSchedulerIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Kafka 보내기를 끈 컨텍스트에는 상품 아웃박스 스케줄러가 없다")
    void noSchedulerWhenKafkaProducerDisabled() {
        assertThat(context.getBeanNamesForType(ProductOutboxScheduler.class)).isEmpty();
    }
}
