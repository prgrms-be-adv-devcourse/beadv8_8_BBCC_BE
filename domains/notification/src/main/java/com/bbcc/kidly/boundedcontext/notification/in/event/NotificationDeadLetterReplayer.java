package com.bbcc.kidly.boundedcontext.notification.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Message;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * 원래 토픽에 다시 쓰지 않고, 보관한 토픽 · 키 · 본문 · eventType/eventId 헤더로 메시지를 만들어 알림 Kafka 리스너의 처리 메서드를 직접 부른다.
 * 원래 토픽은 다른 도메인의 것이고 다른 도메인도 받으므로 다시 보내면 그쪽도 또 받는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationDeadLetterReplayer implements NotificationDeadLetterReplay {

    private final NotificationMemberEventListener memberListener;
    private final NotificationProductEventListener productListener;
    private final NotificationPaymentEventListener paymentListener;
    private final NotificationSellerEventListener sellerListener;
    private final NotificationOrderEventListener orderListener;
    private final NotificationAuthEventListener authListener;

    @Override
    public void replay(Message message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add(NotificationDeadLetterRecords.EVENT_TYPE, message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add(NotificationDeadLetterRecords.EVENT_ID, message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case NotificationKafkaListenerConfig.MEMBER_EVENTS -> memberListener.on(record);
            case NotificationKafkaListenerConfig.PRODUCT_EVENTS -> productListener.on(record);
            case NotificationKafkaListenerConfig.PAYMENT_EVENTS -> paymentListener.on(record);
            case NotificationKafkaListenerConfig.SELLER_EVENTS -> sellerListener.on(record);
            case NotificationKafkaListenerConfig.ORDER_EVENTS -> orderListener.on(record);
            case NotificationKafkaListenerConfig.AUTH_EVENTS -> authListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 알림 이벤트입니다.");
        }
    }
}
