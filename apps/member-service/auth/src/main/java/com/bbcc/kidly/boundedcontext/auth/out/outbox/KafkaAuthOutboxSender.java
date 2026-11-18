package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import com.bbcc.kidly.member.support.outbox.OutboxSendException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
class KafkaAuthOutboxSender implements AuthOutboxSender, DisposableBean {
    private final KafkaTemplate<String, String> template;

    @Override
    public void send(OutboxMessage message) {
        var record = new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
        record.headers().add("eventType", message.getEventType().getBytes(UTF_8))
            .add("eventId", message.getEventId().toString().getBytes(UTF_8));
        try {
            template.send(record).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(message, e);
        } catch (Exception e) {
            throw failure(message, NestedExceptionUtils.getMostSpecificCause(e));
        }
    }

    private OutboxSendException failure(OutboxMessage message, Throwable cause) {
        return new OutboxSendException("eventType=" + message.getEventType() + " eventId=" + message.getEventId()
            + " key=" + message.getMessageKey() + " reason=" + cause.getClass().getSimpleName(), cause);
    }

    @Override
    public void destroy() {
        template.destroy();
    }
}
