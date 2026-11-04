package com.bbcc.kidly.boundedcontext.auth.in;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.auth.app.ChildProfileReplicaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ChildProfileEventListenerTest {

    @Test
    void receivesChildChanged() {
        var service = mock(ChildProfileReplicaService.class);
        var listener = new ChildProfileEventListener(service, JsonMapper.builder().build());
        var record = new ConsumerRecord<String, String>("kidly.childprofile.events", 0, 0, "1",
            "{\"data\":{\"childId\":1,\"memberId\":2,\"deleted\":false}}");
        record.headers().add("eventType", "child.changed".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        listener.on(record);

        verify(service).apply(1L, 2L, false);
    }
}
