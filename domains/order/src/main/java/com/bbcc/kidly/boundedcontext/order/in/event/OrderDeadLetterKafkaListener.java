package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.OrderDeadLetterService;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import java.util.concurrent.atomic.AtomicReference;
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
 *       (OrderKafkaListenerConfig). 실패 로그는 같은 메시지의 첫 실패만 ERROR 한 줄, 이후 재시도는 DEBUG(로그가 끝없이 쌓이지
 *       않게). eventType · eventId · 키 · 예외 종류만 남긴다(본문 · 예외 메시지 없음)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDeadLetterKafkaListener {

    // 주문 Kafka 리스너 이름 규칙: order + 받는 것 + KafkaListener
    static final String ID = "orderDeadLetterKafkaListener";

    private final OrderDeadLetterService deadLetterService;
    // 저장에 실패해 다시 시도 중인 메시지의 위치. 같은 메시지의 이후 실패는 DEBUG로만 남긴다(DLT 컨테이너는 스레드 1개)
    private final AtomicReference<String> failingPosition = new AtomicReference<>();

    @KafkaListener(id = ID, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY,
        topics = {
            OrderKafkaListenerConfig.PRODUCT_EVENTS_DLT,
            OrderKafkaListenerConfig.SELLER_EVENTS_DLT,
            OrderKafkaListenerConfig.CHILDPROFILE_EVENTS_DLT,
            OrderKafkaListenerConfig.PAYMENT_EVENTS_DLT})
    public void on(ConsumerRecord<String, String> record) {
        ReceiveDeadLetterCommand command = OrderDeadLetterRecords.command(record);
        String position = record.topic() + "-" + record.partition() + "@" + record.offset();
        try {
            deadLetterService.receive(command);
            failingPosition.compareAndSet(position, null);
        } catch (RuntimeException e) {
            String exception = NestedExceptionUtils.getMostSpecificCause(e).getClass().getName();
            if (position.equals(failingPosition.getAndSet(position))) {
                log.debug("DLT 메시지를 저장하지 못했습니다(다시 시도 중). topic={} eventType={} eventId={} key={} "
                    + "exception={}", record.topic(), command.eventType(), command.eventId(), record.key(), exception);
            } else {
                log.error("DLT 메시지를 저장하지 못했습니다. 저장될 때까지 다시 시도합니다(같은 메시지의 이후 실패는 DEBUG). "
                    + "topic={} eventType={} eventId={} key={} exception={}", record.topic(), command.eventType(),
                    command.eventId(), record.key(), exception);
            }
            throw e;
        }
    }
}
