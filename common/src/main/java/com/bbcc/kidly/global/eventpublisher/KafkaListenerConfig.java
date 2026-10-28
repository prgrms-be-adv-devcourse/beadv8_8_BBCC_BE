package com.bbcc.kidly.global.eventpublisher;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka로 받는 쪽 공통 설정. 리스너가 실패하면 간격을 두고 다시 시도하고, 그래도 실패하면 ERROR 로그를 남기고 다음 메시지로 넘어간다(DLQ 없음).
 * 읽을 수 없는 메시지(역직렬화 실패)는 다시 시도하지 않는다. 직렬화 설정은 kafka.yml에 있다.
 */
@Slf4j
@Configuration
public class KafkaListenerConfig {

    static final Duration RETRY_INTERVAL = Duration.ofSeconds(1);
    static final long RETRY_COUNT = 2;

    // Boot가 이 빈을 기본 리스너 컨테이너 팩토리에 넣는다
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(
            (consumerRecord, e) -> log.error("이벤트를 처리하지 못했고 더 시도하지 않습니다. topic={} key={} offset={} reason={}",
                consumerRecord.topic(), consumerRecord.key(), consumerRecord.offset(), e.getMessage(), e),
            new FixedBackOff(RETRY_INTERVAL.toMillis(), RETRY_COUNT));
    }
}
