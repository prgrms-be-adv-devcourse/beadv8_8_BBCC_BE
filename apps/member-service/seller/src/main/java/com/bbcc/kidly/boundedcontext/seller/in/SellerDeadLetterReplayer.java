package com.bbcc.kidly.boundedcontext.seller.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerDeadLetterReplay;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerDeadLetterReplayer implements SellerDeadLetterReplay {

    private final MemberEventListener memberEventListener;

    @Override
    public void replay(SellerDeadLetterMessage message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add("eventType", message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add("eventId", message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case SellerKafkaListenerConfig.MEMBER_EVENTS -> memberEventListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 판매자 이벤트입니다.");
        }
    }
}
