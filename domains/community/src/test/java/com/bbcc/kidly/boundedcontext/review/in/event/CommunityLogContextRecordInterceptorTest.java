package com.bbcc.kidly.boundedcontext.review.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 커뮤니티 Kafka 받기의 로그 추적 값: 메시지 하나를 처리하는 동안 헤더 eventId · eventType과 키(correlationId)를 MDC에 둔다.
 */
class CommunityLogContextRecordInterceptorTest {

    private final CommunityLogContextRecordInterceptor interceptor = new CommunityLogContextRecordInterceptor();
    private final Consumer<Object, Object> consumer = mock();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("처리 전에 헤더 eventId · eventType, 키 = correlationId, traceId = correlationId를 넣고, 처리 뒤 지운다")
    void putsAndClears() {
        ConsumerRecord<Object, Object> record = record("42", "product.changed", "e-1");

        assertThat(interceptor.intercept(record, consumer)).isSameAs(record);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "eventId", "e-1", "eventType", "product.changed", "correlationId", "42", "traceId", "42"));

        interceptor.success(record, consumer);
        interceptor.afterRecord(record, consumer);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("처리가 실패해도 처리 뒤 지운다")
    void clearsAfterFailure() {
        ConsumerRecord<Object, Object> record = record("42", "product.changed", "e-1");
        interceptor.intercept(record, consumer);

        interceptor.failure(record, new IllegalStateException("실패"), consumer);
        interceptor.afterRecord(record, consumer);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("같은 스레드가 다음 메시지를 받으면 그 메시지 값만 있고 앞 메시지 값이 남지 않는다")
    void nextRecordOnSameThread() {
        ConsumerRecord<Object, Object> first = record("42", "product.changed", "e-1");
        interceptor.intercept(first, consumer);
        interceptor.afterRecord(first, consumer);
        ConsumerRecord<Object, Object> second = record(null, "product.changed", null);

        interceptor.intercept(second, consumer);

        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "eventType", "product.changed"));
    }

    @Test
    @DisplayName("처리 뒤 정리가 불리지 않고 스레드 상태를 정리하면(컨테이너 멈춤 등) 남은 값을 지운다")
    void clearThreadState() {
        interceptor.intercept(record("42", "product.changed", "e-1"), consumer);

        interceptor.clearThreadState(consumer);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private static ConsumerRecord<Object, Object> record(String key, String eventType, String eventId) {
        var record = new ConsumerRecord<Object, Object>("kidly.product.events", 0, 1L, key, "{}");
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(UTF_8));
        }
        if (eventId != null) {
            record.headers().add("eventId", eventId.getBytes(UTF_8));
        }
        return record;
    }
}
