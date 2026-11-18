package com.bbcc.kidly.boundedcontext.auth.in;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.auth.app.port.AuthDeadLetterReplay;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthDeadLetterReplayer implements AuthDeadLetterReplay {

    private final MemberWithdrawnEventListener memberWithdrawnEventListener;
    private final ChildProfileEventListener childProfileEventListener;

    @Override
    public void replay(DeadLetterMessage message) {
        var record = new ConsumerRecord<>(message.sourceTopic(), 0, 0L, message.messageKey(), message.payload());
        if (message.eventType() != null) {
            record.headers().add("eventType", message.eventType().getBytes(UTF_8));
        }
        if (message.eventId() != null) {
            record.headers().add("eventId", message.eventId().getBytes(UTF_8));
        }
        switch (message.sourceTopic()) {
            case AuthKafkaListenerConfig.MEMBER_EVENTS -> memberWithdrawnEventListener.on(record);
            case AuthKafkaListenerConfig.CHILD_PROFILE_EVENTS -> childProfileEventListener.on(record);
            default -> throw new IllegalArgumentException("재처리할 수 없는 인증 이벤트입니다.");
        }
    }
}
