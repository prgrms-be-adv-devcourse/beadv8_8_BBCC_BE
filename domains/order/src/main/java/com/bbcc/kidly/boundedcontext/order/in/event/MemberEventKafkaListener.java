package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.in.config.OrderKafkaListenerConfig;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.withdrawn을 받아 회원 탈퇴 정리를 그대로 부른다(#1001). Kafka로만 받는다(판매자 · 자녀 계정과 같음).
 * 처리는 처리 메서드가 여는 트랜잭션에서 끝나고, 실패하면 예외를 그대로 던져 재시도 → DLT(kidly.member.events.order.dlt)로 보낸다.
 * 같은 메시지를 다시 받아도 결과는 1회분이다(이미 취소된 주문 · 빈 장바구니는 대상이 아님). 다른 회원 이벤트는 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class MemberEventKafkaListener {

    static final String ID = "orderMemberEventKafkaListener";

    private final JsonMapper jsonMapper;
    private final MemberEventListener memberEventListener;

    @KafkaListener(id = ID, topics = OrderKafkaListenerConfig.MEMBER_EVENTS, groupId = OrderKafkaListenerConfig.GROUP,
        containerFactory = OrderKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        switch (OrderKafkaMessages.eventType(record)) {
            case "member.withdrawn" -> memberEventListener.onMemberWithdrawn(
                jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> {
                // 주문이 받지 않는 종류
            }
        }
    }
}
