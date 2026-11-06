package com.bbcc.kidly.boundedcontext.member.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberDeadLetterReplay;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

/**
 * 원래 토픽(kidly.seller.events · kidly.member.events)은 다른 도메인도 받으므로 다시 보내면 그쪽도 또 받는다.
 */
@Component
@RequiredArgsConstructor
public class MemberDeadLetterReplayer implements MemberDeadLetterReplay {

    private final SellerEventListener sellerEventListener;
    private final ShippingAddressMemberEventListener shippingAddressMemberEventListener;

    @Override
    public void replay(MemberDeadLetterMessage message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add("eventType", message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add("eventId", message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case MemberKafkaListenerConfig.SELLER_EVENTS -> sellerEventListener.on(record);
            case MemberKafkaListenerConfig.MEMBER_EVENTS -> shippingAddressMemberEventListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 회원 이벤트입니다.");
        }
    }
}
