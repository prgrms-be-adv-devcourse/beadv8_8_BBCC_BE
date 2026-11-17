package com.bbcc.kidly.boundedcontext.childprofile.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.ChildProfileDeadLetterReplay;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChildProfileDeadLetterReplayer implements ChildProfileDeadLetterReplay {

    private final ChildProfileMemberEventListener memberEventListener;
    private final ChildProfileAuthEventListener authEventListener;

    @Override
    public void replay(ChildProfileDeadLetterMessage message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add("eventType", message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add("eventId", message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case ChildProfileKafkaListenerConfig.MEMBER_EVENTS -> memberEventListener.on(record);
            case ChildProfileKafkaListenerConfig.AUTH_EVENTS -> authEventListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 자녀 프로필 이벤트입니다.");
        }
    }
}
