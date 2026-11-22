package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDeadLetterService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * DLT 토픽, 파티션, 오프셋마다 1행만 저장한다.
 * 저장하기 전에는 처리 완료로 넘기지 않고 저장될 때까지 재시도한다(SettlementDeadLetterKafkaListenerConfig).
 * 본문은 DB에만 두고 로그에는 위치, eventType, eventId, 예외 종류만 남긴다. 같은 위치가 계속 실패하면 첫 실패만 ERROR로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementDeadLetterKafkaListener {

    public static final String ID = "settlementDeadLetterKafkaListener";

    private final SettlementDeadLetterService service;
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = ID, groupId = SettlementDeadLetterKafkaListenerConfig.GROUP,
        containerFactory = SettlementDeadLetterKafkaListenerConfig.FACTORY,
        topics = {SettlementDeadLetterKafkaListenerConfig.ORDER_EVENTS_DLT,
            SettlementDeadLetterKafkaListenerConfig.PAYMENT_EVENTS_DLT},
        autoStartup = SettlementKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        var position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            if (service.receive(SettlementDeadLetterRecords.from(record))) {
                log.error("정산 DLT 보관: topic={} partition={} offset={} eventType={} eventId={}",
                    record.topic(), record.partition(), record.offset(),
                    SettlementDeadLetterRecords.header(record, SettlementKafkaListenerConfig.EVENT_TYPE),
                    SettlementDeadLetterRecords.header(record, SettlementKafkaListenerConfig.EVENT_ID));
            }
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            var type = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("정산 DLT 보관 재시도: position={} exception={}", position, type);
            } else {
                log.error("정산 DLT 보관 실패(저장될 때까지 재시도): position={} exception={}", position, type);
            }
            throw e;
        }
    }
}
