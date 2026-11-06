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
 * kidly.product.events에서 product.changed를 받아 알림 문구에 쓸 상품 이름 복사본을 저장하고,
 * 판매자에게 관리자 판매 중지 · 옵션 품절을 알린다.
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
            default -> { }  // 알림이 받지 않는 종류
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
