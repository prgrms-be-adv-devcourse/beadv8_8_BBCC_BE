package com.bbcc.kidly.boundedcontext.review.in.event;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * 커뮤니티 Kafka 받기의 로그 추적 값. 커뮤니티 팩토리의 모든 컨테이너가 쓴다(CommunityKafkaListenerConfig).
 * 메시지 하나를 처리하는 동안 헤더 eventId · eventType과 키(팀 규칙: 키 = correlationId)를 MDC에 두고, 처리가 끝나면(성공 · 실패,
 * 에러 처리기 뒤) 연 때의 값으로 되돌린다. 본문은 읽지 않는다. 소비 스레드마다 하나씩 열리므로 스레드 로컬로 들고 있다.
 */
public class CommunityLogContextRecordInterceptor implements RecordInterceptor<Object, Object> {

    private static final ThreadLocal<CommunityLogContext> CURRENT = new ThreadLocal<>();

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
        Consumer<Object, Object> consumer) {
        close();
        CURRENT.set(CommunityLogContext.open(CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_ID),
            CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_TYPE),
            record.key() == null ? null : String.valueOf(record.key())));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        close();
    }

    // 처리 뒤 정리가 불리지 않은 채 스레드를 정리할 때(컨테이너 멈춤 등)
    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        close();
    }

    private static void close() {
        CommunityLogContext context = CURRENT.get();
        if (context != null) {
            CURRENT.remove();
            context.close();
        }
    }
}
