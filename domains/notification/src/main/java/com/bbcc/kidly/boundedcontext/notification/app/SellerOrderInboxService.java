package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import com.bbcc.kidly.boundedcontext.notification.app.dto.PurchaseConfirmedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 알림함에 새 주문 · 구매확정을 넣는다. 다음에 들어왔을 때 알면 되는 일이라 메일은 보내지 않는다.
 * 판매자 센터의 주문 화면 주소가 정해지지 않아 링크는 두지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SellerOrderInboxService {

    private final InboxNotificationService inboxNotificationService;
    private final ProductNameService productNameService;

    /**
     * 한 주문에 판매자가 여럿이면 판매자마다 자기 상품만으로 한 건씩 넣는다(중복 키 = 주문 ID + 판매자 ID).
     */
    @Transactional
    public void addOrderPaid(OrderPaidCommand command) {
        var itemsBySeller = command.items().stream()
            .collect(Collectors.groupingBy(OrderPaidCommand.Item::sellerId, LinkedHashMap::new, Collectors.toList()));
        itemsBySeller.forEach((sellerId, items) -> inboxNotificationService.add(new InboxNotificationInfo(
            InboxRecipient.member(sellerId), NotificationType.SELLER_ORDER_PAID, "새 주문이 들어왔어요",
            orderPaidBody(items), null,
            "order.paid:%d:%d".formatted(command.orderId(), sellerId))));
    }

    // 상품 이름 복사본이 없으면(아직 상품 이벤트를 받지 못했다) 이름 없이 쓴다
    @Transactional
    public void addPurchaseConfirmed(PurchaseConfirmedCommand command) {
        var body = productNameService.findName(command.productId())
            .map(name -> "%s %d개가 구매확정돼 정산 대상이 됐어요.".formatted(name, command.quantity()))
            .orElse("주문상품 %d개가 구매확정돼 정산 대상이 됐어요.".formatted(command.quantity()));
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.sellerId()),
            NotificationType.SELLER_PURCHASE_CONFIRMED, "구매가 확정됐어요", body, null,
            "order-item.purchase-confirmed:" + command.orderItemId()));
    }

    private static String orderPaidBody(List<OrderPaidCommand.Item> items) {
        var first = items.getFirst().productName();
        var name = items.size() == 1 ? first : "%s 외 %d건".formatted(first, items.size() - 1);
        return "%s 주문이 결제됐어요. 발송을 준비해 주세요.".formatted(name);
    }
}
