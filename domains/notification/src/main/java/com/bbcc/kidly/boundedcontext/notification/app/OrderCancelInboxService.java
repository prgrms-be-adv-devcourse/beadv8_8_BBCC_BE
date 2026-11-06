package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderItemCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자가 직접 하지 않은 주문 취소(재고 부족 · 결제 기한 지남 · 판매자 취소)를 구매자 알림함에 넣는다.
 * 같은 내용을 웹 푸시로도 보낸다. 누르면 그 주문 상세로 간다. 같은 주문 취소 · 같은 판매자 취소는 한 건만 넣는다.
 */
@Service
@RequiredArgsConstructor
public class OrderCancelInboxService {

    private final InboxNotificationService inboxNotificationService;
    private final NotificationService notificationService;

    @Transactional
    public void addOrderCancelled(OrderCancelledCommand command) {
        OrderCancelInbox.orderCancelled(command.reason()).ifPresent(content ->
            add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
                NotificationType.ORDER_CANCELLED, content.title(), content.body(), orderPath(command.orderId()),
                "order.cancelled:" + command.orderId())));
    }

    @Transactional
    public void addItemsCancelledBySeller(OrderItemCancelledCommand command) {
        var content = OrderCancelInbox.itemsCancelledBySeller(command.reason(), command.itemCount());
        add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.ORDER_ITEM_CANCELLED, content.title(), content.body(), orderPath(command.orderId()),
            "order-item.cancelled:" + command.cancellationId()));
    }

    private void add(InboxNotificationInfo inbox) {
        inboxNotificationService.add(inbox);
        notificationService.createPush(inbox);
    }

    private static String orderPath(Long orderId) {
        return "/orders/" + orderId;
    }
}
