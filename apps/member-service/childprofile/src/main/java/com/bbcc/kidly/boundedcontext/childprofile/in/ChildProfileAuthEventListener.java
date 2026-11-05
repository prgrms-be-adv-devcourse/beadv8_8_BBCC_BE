package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildAccountLinkService;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChildProfileAuthEventListener {
    private final ChildAccountLinkService service;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "childProfileAuthEventListener", topics = "kidly.auth.events",
        groupId = "kidly.childprofile", containerFactory = "childprofileKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (ChildProfileKafkaListenerConfig.header(record, "eventType")) {
            case null -> throw new IllegalArgumentException("eventType 헤더가 없습니다");
            case "child-account.changed" -> {
                var event = jsonMapper.readValue(record.value(), ChildAccountChangedEvent.class);
                var data = event.data();
                service.applyAccountChange(data.accountId(), data.childProfileId(), data.deleted(), event.occurredAt());
            }
            default -> { }
        }
    }
}
