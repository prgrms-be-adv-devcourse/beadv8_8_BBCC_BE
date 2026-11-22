package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.ProductNameService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.ProductChangedCommand;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 메시지를 다시 받거나 옛 메시지가 늦게 와도 ProductName이 발생 시각으로 거른다.
 */
@Component
@RequiredArgsConstructor
public class NotificationProductEventListener {

    private final ProductNameService productNameService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationProductEventListener", topics = "kidly.product.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "product.changed" -> onChanged(jsonMapper.readValue(record.value(), ProductChangedEvent.class));
            default -> { }
        }
    }

    private void onChanged(ProductChangedEvent event) {
        var data = event.data();
        var options = data.options().stream()
            .map(option -> new ProductChangedCommand.Option(option.optionId(), option.optionName(), option.soldOut()))
            .toList();
        productNameService.save(new ProductChangedCommand(event.eventId(), data.productId(), data.sellerId(),
            data.name(), data.status(), data.stoppedByAdmin(), options, event.occurredAt()));
    }
}
