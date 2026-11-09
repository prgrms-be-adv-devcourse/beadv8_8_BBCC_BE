package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 알림 DLT 여섯 개({받는 토픽}.notification.dlt)를 읽어 notification.dead_letter에 보관한다. DLT 토픽 · 파티션 · 오프셋당 1행이다.
 * 저장하기 전에는 처리 완료로 넘기지 않고 저장될 때까지 재시도한다(NotificationDeadLetterKafkaListenerConfig).
 * 본문은 DB에만 두고 로그에는 위치 · eventType · eventId · 예외 종류만 남긴다. 같은 위치가 계속 실패하면 첫 실패만 ERROR다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeadLetterKafkaListener {

    public static final String ID = "notificationDeadLetterKafkaListener";

    private final NotificationDeadLetterService service;
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = ID, groupId = NotificationDeadLetterKafkaListenerConfig.GROUP,
        containerFactory = NotificationDeadLetterKafkaListenerConfig.FACTORY,
        topics = {NotificationDeadLetterKafkaListenerConfig.MEMBER_EVENTS_DLT,
            NotificationDeadLetterKafkaListenerConfig.PRODUCT_EVENTS_DLT,
            NotificationDeadLetterKafkaListenerConfig.PAYMENT_EVENTS_DLT,
            NotificationDeadLetterKafkaListenerConfig.SELLER_EVENTS_DLT,
            NotificationDeadLetterKafkaListenerConfig.ORDER_EVENTS_DLT,
            NotificationDeadLetterKafkaListenerConfig.AUTH_EVENTS_DLT})
    public void on(ConsumerRecord<String, String> record) {
        String position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            if (service.receive(NotificationDeadLetterRecords.from(record))) {
                log.error("알림 DLT 보관: topic={} partition={} offset={} eventType={} eventId={}",
                    record.topic(), record.partition(), record.offset(),
                    NotificationDeadLetterRecords.header(record, NotificationDeadLetterRecords.EVENT_TYPE),
                    NotificationDeadLetterRecords.header(record, NotificationDeadLetterRecords.EVENT_ID));
            }
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            String type = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("알림 DLT 보관 재시도: position={} exception={}", position, type);
            } else {
                log.error("알림 DLT 보관 실패(저장될 때까지 재시도): position={} exception={}", position, type);
            }
            throw e;
        }
    }
}
