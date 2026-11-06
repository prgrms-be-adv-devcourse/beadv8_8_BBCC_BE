package com.bbcc.kidly.boundedcontext.seller.in;

import static com.bbcc.kidly.boundedcontext.seller.in.SellerKafkaListenerConfig.header;

import com.bbcc.kidly.boundedcontext.seller.app.SellerDeadLetterService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;
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
public class SellerDeadLetterKafkaListener {

    private final SellerDeadLetterService service;
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = "sellerDeadLetterKafkaListener", groupId = SellerDeadLetterKafkaListenerConfig.GROUP,
        containerFactory = SellerDeadLetterKafkaListenerConfig.FACTORY,
        topics = SellerKafkaListenerConfig.MEMBER_EVENTS_DLT)
    public void on(ConsumerRecord<String, String> record) {
        var position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            var message = message(record);
            if (service.receive(message)) {
                log.warn("판매자 DLT 보관: topic={} partition={} offset={} eventType={} eventId={}",
                    record.topic(), record.partition(), record.offset(), message.eventType(), message.eventId());
            }
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            var type = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("판매자 DLT 보관 재시도: position={} exception={}", position, type);
            } else {
                log.error("판매자 DLT 보관 실패(저장될 때까지 재시도): position={} exception={}", position, type);
            }
            throw e;
        }
    }

    private static SellerDeadLetterMessage message(ConsumerRecord<String, String> record) {
        var sourceTopic = Optional.ofNullable(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).orElse(record.topic());
        var sourcePartition = number(record, KafkaHeaders.DLT_ORIGINAL_PARTITION).map(ByteBuffer::getInt).orElse(null);
        var sourceOffset = number(record, KafkaHeaders.DLT_ORIGINAL_OFFSET).map(ByteBuffer::getLong).orElse(null);
        var failureType = Optional.ofNullable(header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
            .orElseGet(() -> header(record, KafkaHeaders.DLT_EXCEPTION_FQCN));
        return new SellerDeadLetterMessage(record.topic(), record.partition(), record.offset(),
            clip(sourceTopic, 100), sourcePartition, sourceOffset, clip(record.key(), 100),
            clip(header(record, "eventType"), 100), clip(header(record, "eventId"), 100), record.value(),
            clip(failureType, 255));
    }

    private static Optional<ByteBuffer> number(ConsumerRecord<?, ?> record, String name) {
        return Optional.ofNullable(record.headers().lastHeader(name)).map(header -> ByteBuffer.wrap(header.value()));
    }

    // 컬럼 길이를 넘거나 NUL이 있으면 저장이 끝없이 실패하고, 줄바꿈은 로그를 위조한다
    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        var text = value.replace((char) 0, '�').replace('\r', ' ').replace('\n', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
