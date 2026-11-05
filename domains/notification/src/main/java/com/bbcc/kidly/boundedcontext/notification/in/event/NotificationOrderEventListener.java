package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.WishInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 조르기 요청을 받아 보호자 알림함에 넣는다.
 * 이 구독 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 보낸 조르기를
 * 건너뛰므로 이 리스너만 처음부터 읽는다. 같은 메시지를 다시 받아도 조르기 ID 중복 키로 한 건만 넣는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationOrderEventListener {

    private final WishInboxService wishInboxService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationOrderEventListener", topics = "kidly.order.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "wish.sent" -> onWishSent(jsonMapper.readValue(record.value(), WishSentEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onWishSent(WishSentEvent event) {
        var data = event.data();
        wishInboxService.addWishSent(new WishSentCommand(data.wishRequestId(), data.childId(), data.memberId(),
            data.items().stream().map(WishSentEvent.Item::productId).toList()));
    }
}
