package com.bbcc.kidly.boundedcontext.payment.in.event;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentEventHandler;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.registered를 받아 지갑을 만들고(#593), member.withdrawn을 받아 지갑을 동결한다(#670).
 * 회원의 다른 종류(변경 · 토큰 무효화)는 건너뛴다.
 * 스위치(kidly.payment.kafka.consumer.enabled)가 꺼져 있으면 시작하지 않고, 그때는 Spring 리스너가 받는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentMemberEventListener {

    private final PaymentEventHandler handler;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "paymentMemberEventListener", topics = PaymentKafkaListenerConfig.MEMBER_EVENTS,
        groupId = PaymentKafkaListenerConfig.GROUP, containerFactory = "paymentKafkaListenerContainerFactory",
        autoStartup = PaymentKafkaListenerConfig.AUTO_STARTUP)
    public void on(ConsumerRecord<String, String> record) {
        switch (PaymentKafkaListenerConfig.eventType(record)) {
            case "member.registered" ->
                handler.memberRegistered(jsonMapper.readValue(record.value(), MemberRegisteredEvent.class));
            case "member.withdrawn" ->
                handler.memberWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
            default -> { }  // 결제가 받지 않는 종류
        }
    }
}
