package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PayoutEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.RefundEmailCommand;
import com.bbcc.kidly.shared.payment.event.PaymentPayoutCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.payment.events에서 정산금 지급 완료 · 환불 완료를 받아 메일을 만든다.
 * 이 구독 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 발행된 지급 · 환불을
 * 건너뛰므로 이 리스너만 처음부터 읽는다. 같은 메시지를 다시 받아도 NotificationService가 한 통만 만든다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPaymentEventListener {

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationPaymentEventListener", topics = "kidly.payment.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "payment.payout-completed" ->
                onPayoutCompleted(jsonMapper.readValue(record.value(), PaymentPayoutCompletedEvent.class));
            case "payment.refunded" -> onRefunded(jsonMapper.readValue(record.value(), PaymentRefundedEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onPayoutCompleted(PaymentPayoutCompletedEvent event) {
        var data = event.data();
        notificationService.createPayoutEmail(
            new PayoutEmailCommand(data.settlementId(), data.sellerId(), data.amount()));
    }

    private void onRefunded(PaymentRefundedEvent event) {
        var data = event.data();
        notificationService.createRefundEmail(
            new RefundEmailCommand(event.eventId(), data.memberId(), data.orderId(), data.amount()));
    }
}
