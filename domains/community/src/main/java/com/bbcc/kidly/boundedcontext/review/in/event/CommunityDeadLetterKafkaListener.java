package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.in.config.CommunityKafkaListenerConfig;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityDeadLetterKafkaListener {

    public static final String ID = "communityDeadLetterKafkaListener";
    private final CommunityDeadLetterService service;
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = ID, groupId = CommunityKafkaListenerConfig.GROUP,
        containerFactory = CommunityKafkaListenerConfig.FACTORY,
        topics = {CommunityKafkaListenerConfig.ORDER_EVENTS_DLT, CommunityKafkaListenerConfig.PRODUCT_EVENTS_DLT,
            CommunityKafkaListenerConfig.MEMBER_EVENTS_DLT})
    public void on(ConsumerRecord<String, String> record) {
        String position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            if (service.receive(CommunityDeadLetterRecords.from(record))) {
                log.error("커뮤니티 DLT 보관: topic={} partition={} offset={} eventType={} eventId={}",
                    record.topic(), record.partition(), record.offset(),
                    CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_TYPE),
                    CommunityKafkaMessages.header(record, CommunityKafkaMessages.EVENT_ID));
            }
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            String type = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("커뮤니티 DLT 보관 재시도: position={} exception={}", position, type);
            } else {
                log.error("커뮤니티 DLT 보관 실패(저장될 때까지 재시도): position={} exception={}", position, type);
            }
            throw e;
        }
    }
}
