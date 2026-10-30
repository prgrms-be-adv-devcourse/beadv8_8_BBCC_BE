package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 DLT 4개(kidly.{product|seller|childprofile|payment}.events.order.dlt)를 읽어 관리자가 볼 수 있게 저장한다(#601).
 * 구독 그룹은 원래 토픽 리스너와 같은 kidly.order, 팩토리도 같다(스위치 · earliest · 한 건씩 완료).
 * <ul>
 *   <li>DLT 메시지는 읽지 못하는 값이 있어도 그대로 저장한다(OrderDeadLetterRecords · 저장 칸 자르기). 실패할 수 있는 것은 저장뿐이다</li>
 *   <li>저장이 실패하면 예외를 다시 던진다. DLT 컨테이너의 에러 처리기가 DLT로 보내지 않고 같은 메시지를 끝없이 다시 시도한다
 *       (OrderKafkaListenerConfig). 실패 로그에는 eventType · eventId · 키 · 예외 종류만 남긴다(본문 · 예외 메시지 없음)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDeadLetterKafkaListener {

    // 주문 Kafka 리스너 이름 규칙: order + 받는 것 + KafkaListener
    static final String ID = "orderDeadLetterKafkaListener";

    private final OrderDeadLetterService deadLetterService;

    @KafkaListener(id = ID, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY,
        topics = {
            OrderKafkaListenerConfig.PRODUCT_EVENTS_DLT,
            OrderKafkaListenerConfig.SELLER_EVENTS_DLT,
            OrderKafkaListenerConfig.CHILDPROFILE_EVENTS_DLT,
            OrderKafkaListenerConfig.PAYMENT_EVENTS_DLT})
    public void on(ConsumerRecord<String, String> record) {
        ReceiveDeadLetterCommand command = OrderDeadLetterRecords.command(record);
        try {
            deadLetterService.receive(command);
        } catch (RuntimeException e) {
            log.warn("DLT 메시지를 저장하지 못해 다시 시도합니다. topic={} eventType={} eventId={} key={} exception={}",
                record.topic(), command.eventType(), command.eventId(), record.key(),
                NestedExceptionUtils.getMostSpecificCause(e).getClass().getName());
            throw e;
        }
    }
}
