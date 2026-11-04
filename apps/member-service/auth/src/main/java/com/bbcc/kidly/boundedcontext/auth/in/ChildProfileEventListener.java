package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.ChildProfileReplicaService;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChildProfileEventListener {

    private final ChildProfileReplicaService service;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "authChildProfileEventListener", topics = "kidly.childprofile.events",
        groupId = "kidly.auth", containerFactory = "authKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        if ("child.changed".equals(AuthKafkaListenerConfig.header(record, "eventType"))) {
            var data = jsonMapper.readValue(record.value(), ChildChangedEvent.class).data();
            service.apply(data.childId(), data.memberId(), data.deleted());
        }
    }
}
