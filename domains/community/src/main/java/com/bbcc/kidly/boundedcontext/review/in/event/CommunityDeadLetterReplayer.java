package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Message;
import com.bbcc.kidly.boundedcontext.review.in.config.CommunityKafkaListenerConfig;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * 원래 토픽에 쓰지 않고 동일 Kafka decode/검증/처리 메서드를 직접 호출한다.
 */
@Component
@RequiredArgsConstructor
public class CommunityDeadLetterReplayer implements CommunityDeadLetterReplay {

    private final CommunityOrderEventKafkaListener orderListener;
    private final CommunityProductEventKafkaListener productListener;
    private final CommunityMemberEventKafkaListener memberListener;

    @Override
    public void replay(Message message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add(CommunityKafkaMessages.EVENT_TYPE,
                message.eventType().getBytes(StandardCharsets.UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add(CommunityKafkaMessages.EVENT_ID, message.eventId().getBytes(StandardCharsets.UTF_8));
        }
        boolean handled = switch (message.sourceTopic()) {
            case CommunityKafkaListenerConfig.ORDER_EVENTS -> orderListener.handle(record);
            case CommunityKafkaListenerConfig.PRODUCT_EVENTS -> productListener.handle(record);
            case CommunityKafkaListenerConfig.MEMBER_EVENTS -> memberListener.handle(record);
            default -> false;
        };
        if (!handled) {
            throw new IllegalArgumentException("재처리할 수 없는 커뮤니티 이벤트입니다.");
        }
    }
}
