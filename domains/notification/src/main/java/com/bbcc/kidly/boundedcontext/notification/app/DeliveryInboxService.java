package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.DeliveryCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 출발 · 완료를 구매자 알림함에 넣고 같은 내용을 웹 푸시로 보낸다. 기록으로 남길 일은 아니라 메일은 보내지 않는다.
 * 배송은 주문 × 판매자 단위라 한 주문에서 판매자 수만큼 들어올 수 있다. 누르면 그 주문 상세로 간다.
 * 같은 배송의 출발 · 완료는 한 건씩만 넣는다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryInboxService {

    private static final String SHIPPED_BODY = "%s 운송장 %s로 출발했어요.";
    private static final String SHIPPED_BODY_WITHOUT_TRACKING = "주문하신 상품이 출발했어요. 주문 상세에서 배송 상태를 확인해 주세요.";
    private static final String DELIVERED_BODY = "주문하신 상품이 도착했어요. 받은 상품을 확인해 주세요.";

    private final InboxNotificationService inboxNotificationService;
    private final NotificationService notificationService;

    @Transactional
    public void addShipped(DeliveryCommand command) {
        add(command, NotificationType.DELIVERY_SHIPPED, "주문하신 상품이 출발했어요",
            shippedBody(command), "delivery.shipped:");
    }

    // 택배사 · 운송장 중 하나라도 없으면 "null 운송장 null"이 되지 않게 운송장 없는 문구를 쓴다
    private static String shippedBody(DeliveryCommand command) {
        if (command.carrierName() == null || command.trackingNumber() == null) {
            return SHIPPED_BODY_WITHOUT_TRACKING;
        }
        return SHIPPED_BODY.formatted(command.carrierName(), command.trackingNumber());
    }

    @Transactional
    public void addDelivered(DeliveryCommand command) {
        add(command, NotificationType.DELIVERY_DELIVERED, "배송이 완료됐어요", DELIVERED_BODY,
            "delivery.delivered:");
    }

    private void add(DeliveryCommand command, NotificationType type, String title, String body, String keyPrefix) {
        var inbox = new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            type, title, body,
            "/orders/" + command.orderId(), keyPrefix + command.deliveryId());
        inboxNotificationService.add(inbox);
        notificationService.createPush(inbox);
    }
}
