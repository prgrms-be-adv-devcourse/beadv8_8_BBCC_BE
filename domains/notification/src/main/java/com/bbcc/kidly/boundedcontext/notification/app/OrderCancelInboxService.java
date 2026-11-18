package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderItemCancelledCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
