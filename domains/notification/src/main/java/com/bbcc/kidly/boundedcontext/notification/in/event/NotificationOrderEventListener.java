package com.bbcc.kidly.boundedcontext.notification.in.event;

import com.bbcc.kidly.boundedcontext.notification.app.DeliveryInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.OrderCancelInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.WishInboxService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.DeliveryCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderItemCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WishSentCommand;
import com.bbcc.kidly.shared.order.event.DeliveryDeliveredEvent;
import com.bbcc.kidly.shared.order.event.DeliveryShippedEvent;
import com.bbcc.kidly.shared.order.event.OrderCancelledEvent;
import com.bbcc.kidly.shared.order.event.OrderItemCancelledEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.order.events에서 조르기 요청은 보호자 알림함에, 구매자가 하지 않은 주문 취소
 * (재고 부족 · 결제 기한 지남 · 판매자 취소)와 배송 출발 · 완료는 구매자 알림함에 넣는다.
 * 이 구독 그룹은 이 토픽을 처음 읽으므로 책갈피가 없다. 앱 기본값(latest)이면 구독 전에 보낸 이벤트를
 * 건너뛰므로 이 리스너만 처음부터 읽는다. 같은 메시지를 다시 받아도 중복 키(조르기 ID · 주문 ID · 취소 ID · 배송 ID)로 한 건만 넣는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationOrderEventListener {

    private final WishInboxService wishInboxService;
    private final OrderCancelInboxService orderCancelInboxService;
    private final DeliveryInboxService deliveryInboxService;
    private final JsonMapper jsonMapper;

    @KafkaListener(id = "notificationOrderEventListener", topics = "kidly.order.events",
        groupId = "kidly.notification", containerFactory = "notificationKafkaListenerContainerFactory",
        properties = "auto.offset.reset=earliest")
    public void on(ConsumerRecord<String, String> record) {
        switch (NotificationKafkaListenerConfig.header(record, "eventType")) {
            case "wish.sent" -> onWishSent(jsonMapper.readValue(record.value(), WishSentEvent.class));
            case "order.cancelled" ->
                onOrderCancelled(jsonMapper.readValue(record.value(), OrderCancelledEvent.class));
            case "order-item.cancelled" ->
                onOrderItemCancelled(jsonMapper.readValue(record.value(), OrderItemCancelledEvent.class));
            case "delivery.shipped" ->
                onDeliveryShipped(jsonMapper.readValue(record.value(), DeliveryShippedEvent.class));
            case "delivery.delivered" ->
                onDeliveryDelivered(jsonMapper.readValue(record.value(), DeliveryDeliveredEvent.class));
            default -> { }  // 알림이 받지 않는 종류
        }
    }

    private void onWishSent(WishSentEvent event) {
        var data = event.data();
        wishInboxService.addWishSent(new WishSentCommand(data.wishRequestId(), data.childId(), data.memberId(),
            data.items().stream().map(WishSentEvent.Item::productId).toList()));
    }

    private void onOrderCancelled(OrderCancelledEvent event) {
        var data = event.data();
        orderCancelInboxService.addOrderCancelled(
            new OrderCancelledCommand(data.orderId(), data.memberId(), data.reason()));
    }

    private void onOrderItemCancelled(OrderItemCancelledEvent event) {
        var data = event.data();
        orderCancelInboxService.addItemsCancelledBySeller(new OrderItemCancelledCommand(data.cancellationId(),
            data.orderId(), data.memberId(), data.reason(), data.items().size()));
    }

    private void onDeliveryShipped(DeliveryShippedEvent event) {
        var data = event.data();
        deliveryInboxService.addShipped(new DeliveryCommand(data.deliveryId(), data.orderId(), data.memberId(),
            data.carrierName(), data.trackingNumber()));
    }

    private void onDeliveryDelivered(DeliveryDeliveredEvent event) {
        var data = event.data();
        deliveryInboxService.addDelivered(new DeliveryCommand(data.deliveryId(), data.orderId(), data.memberId(),
            data.carrierName(), data.trackingNumber()));
    }
}
