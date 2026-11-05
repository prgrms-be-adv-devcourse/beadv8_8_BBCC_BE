package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.SellerStatusService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.SellerChangedCommand;
import com.bbcc.kidly.shared.seller.event.SellerApplicationRejectedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.seller.events에서 판매자 신청 거절을 받아 신청자에게 거절 사유 메일을, 판매자 변경을 받아 승인 · 정지 메일을 만든다.
 * 이 구독 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 발행된 거절을
 * 건너뛰므로 이 리스너만 처음부터 읽는다. 같은 메시지를 다시 받아도 한 통만 만든다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSellerEventListener {

    private final NotificationService notificationService;
    private final SellerStatusService sellerStatusService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationSellerEventListener", topics = "kidly.seller.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "seller.application-rejected" ->
                onApplicationRejected(jsonMapper.readValue(record.value(), SellerApplicationRejectedEvent.class));
            case "seller.changed" -> onChanged(jsonMapper.readValue(record.value(), SellerChangedEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onApplicationRejected(SellerApplicationRejectedEvent event) {
        notificationService.createApplicationRejectedEmail(event.eventId(), event.data().memberId(),
            event.data().reason());
    }

    private void onChanged(SellerChangedEvent event) {
        sellerStatusService.apply(new SellerChangedCommand(event.eventId(), event.data().sellerId(),
            event.data().businessName(), event.data().status(), event.occurredAt()));
    }
}
