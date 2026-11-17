package com.bbcc.kidly.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "kidly.kafka.producer.enabled=true",
    "spring.kafka.admin.auto-create=true"
})
public @interface KafkaIntegrationTest {
}
