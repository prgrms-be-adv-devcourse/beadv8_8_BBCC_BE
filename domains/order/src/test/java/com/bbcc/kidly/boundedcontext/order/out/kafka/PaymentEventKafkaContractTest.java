package com.bbcc.kidly.boundedcontext.order.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.in.event.PaymentEventKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * 결제 마감 만료 전 대기량 확인(#561)이 실제로 결제 이벤트를 받는 리스너(#594)의 토픽 · 구독 그룹을 본다는 것을 고정한다.
 * 한쪽만 바뀌면 대기량 확인이 엉뚱한 그룹 · 토픽을 보고 늘 대기 0(또는 배정 전)이라 답한다.
 */
class PaymentEventKafkaContractTest {

    @Test
    @DisplayName("결제 이벤트 리스너의 토픽 · 그룹이 대기량 확인의 토픽 · 그룹과 같고, id는 주문 리스너 이름 규칙을 따른다")
    void listenerAndLagCheckWatchSameTopicAndGroup() throws NoSuchMethodException {
        KafkaListener listener = PaymentEventKafkaListener.class.getMethod("on", ConsumerRecord.class)
            .getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(KafkaPaymentEventLag.TOPIC);
        assertThat(listener.groupId()).isEqualTo(KafkaPaymentEventLag.GROUP);
        assertThat(listener.id()).isEqualTo("orderPaymentEventKafkaListener");
    }
}
