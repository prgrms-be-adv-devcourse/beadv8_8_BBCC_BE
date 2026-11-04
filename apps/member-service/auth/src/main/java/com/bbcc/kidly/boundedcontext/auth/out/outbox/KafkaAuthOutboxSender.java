package com.bbcc.kidly.boundedcontext.auth.out.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSendException;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.out.kafka.KafkaAuthEventSender;
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
    public void send(AuthOutboxMessage message) {
        var record = new ProducerRecord<>(KafkaAuthEventSender.TOPIC, message.getMessageKey(), message.getPayload());
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

    private AuthOutboxSendException failure(AuthOutboxMessage message, Throwable cause) {
        return new AuthOutboxSendException("eventType=" + message.getEventType() + " eventId=" + message.getEventId()
            + " key=" + message.getMessageKey() + " reason=" + cause.getClass().getSimpleName());
    }

    @Override
    public void destroy() {
        template.destroy();
    }
}
