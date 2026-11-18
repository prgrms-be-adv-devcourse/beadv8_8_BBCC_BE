package com.bbcc.kidly.boundedcontext.member.in;

import static com.bbcc.kidly.boundedcontext.member.in.MemberKafkaListenerConfig.header;

import com.bbcc.kidly.boundedcontext.member.app.MemberDeadLetterService;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberDeadLetterKafkaListener {

    private final MemberDeadLetterService service;
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = "memberDeadLetterKafkaListener", groupId = MemberDeadLetterKafkaListenerConfig.GROUP,
        containerFactory = MemberDeadLetterKafkaListenerConfig.FACTORY,
        topics = {MemberKafkaListenerConfig.SELLER_EVENTS_DLT, MemberKafkaListenerConfig.MEMBER_EVENTS_DLT})
    public void on(ConsumerRecord<String, String> record) {
        var position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            var message = message(record);
            if (service.receive(message)) {
                log.warn("회원 DLT 보관: topic={} partition={} offset={} eventType={} eventId={}",
                    record.topic(), record.partition(), record.offset(), message.eventType(), message.eventId());
            }
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            var type = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("회원 DLT 보관 재시도: position={} exception={}", position, type);
            } else {
                log.error("회원 DLT 보관 실패(저장될 때까지 재시도): position={} exception={}", position, type);
            }
            throw e;
        }
    }

    private static DeadLetterMessage message(ConsumerRecord<String, String> record) {
        var sourceTopic = Optional.ofNullable(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).orElse(record.topic());
        var sourcePartition = number(record, KafkaHeaders.DLT_ORIGINAL_PARTITION).map(ByteBuffer::getInt).orElse(null);
        var sourceOffset = number(record, KafkaHeaders.DLT_ORIGINAL_OFFSET).map(ByteBuffer::getLong).orElse(null);
        var failureType = Optional.ofNullable(header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
            .orElseGet(() -> header(record, KafkaHeaders.DLT_EXCEPTION_FQCN));
        return new DeadLetterMessage(record.topic(), record.partition(), record.offset(), sourceTopic,
            sourcePartition, sourceOffset, record.key(), header(record, "eventType"), header(record, "eventId"),
            record.value(), failureType);
    }

    private static Optional<ByteBuffer> number(ConsumerRecord<?, ?> record, String name) {
        return Optional.ofNullable(record.headers().lastHeader(name)).map(header -> ByteBuffer.wrap(header.value()));
    }
}
