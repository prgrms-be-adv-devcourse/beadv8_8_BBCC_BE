package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ChildAccountChangedCommand;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.auth.events에서 자녀 계정 생성 · 삭제를 받아 자녀 계정 복사본을 갱신한다(자녀 알림함 받는 사람을 찾는 데 쓴다).
 * 이 구독 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 만든 계정을 모르므로
 * 이 리스너만 처음부터 읽는다. 같은 메시지를 다시 받아도 결과가 같다(ChildAccountService).
 */
@Component
@RequiredArgsConstructor
public class NotificationAuthEventListener {

    private final ChildAccountService childAccountService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationAuthEventListener", topics = "kidly.auth.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    public void on(ConsumerRecord<String, String> record) {
        if ("child-account.changed".equals(NotificationKafkaListenerConfig.header(record, "eventType"))) {
            var data = jsonMapper.readValue(record.value(), ChildAccountChangedEvent.class).data();
            childAccountService.apply(
                new ChildAccountChangedCommand(data.accountId(), data.childProfileId(), data.deleted()));
        }
    }
}
