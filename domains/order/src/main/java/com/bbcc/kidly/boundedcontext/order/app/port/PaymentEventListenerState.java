package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 주문의 결제 이벤트 Kafka 리스너가 실행 중인지(#561). 구현은 out/messaging(Kafka 리스너 레지스트리).
 * 실행 중일 때만 결제 마감 만료가 결제 이벤트 처리 대기량을 확인한다. 리스너가 없거나(결제 이벤트 Kafka 수신 전, #594)
 * 멈춰 있으면(수신 스위치 꺼짐 = Spring 수신) 확인할 대기분이 없다.
 */
public interface PaymentEventListenerState {

    /**
     * 결제 이벤트(kidly.payment.events) Kafka 리스너 id. #594가 리스너를 만들 때 이 값을 쓴다
     * (주문 Kafka 리스너 이름 규칙: order + 받는 이벤트 + KafkaListener).
     */
    String LISTENER_ID = "orderPaymentEventKafkaListener";

    boolean isRunning();
}
