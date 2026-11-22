package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import java.util.List;

/**
 * 알림함 본문은 1000자까지라 첫 상품 이름(255자까지)만 적고 나머지는 "외 N건"으로 줄인다.
 */
public record OrderPaidInbox(String title, String body) {

    public static OrderPaidInbox buyer(OrderPaidCommand command) {
        var total = "총 결제 금액은 %,d원이에요.".formatted(command.totalAmount());
        // 주문은 상품 없이 만들어지지 않지만, 받는 쪽은 상품이 빈 메시지도 실패시키지 않는다
        var body = command.items().isEmpty()
            ? "주문의 결제가 끝났어요. " + total
            : "%s 주문의 결제가 끝났어요. %s".formatted(itemNames(command.items()), total);
        return new OrderPaidInbox("주문이 결제됐어요", body);
    }

    public static OrderPaidInbox seller(List<OrderPaidCommand.Item> items) {
        return new OrderPaidInbox("새 주문이 들어왔어요",
            "%s 주문이 결제됐어요. 발송을 준비해 주세요.".formatted(itemNames(items)));
    }

    private static String itemNames(List<OrderPaidCommand.Item> items) {
        var first = items.getFirst().productName();
        return items.size() == 1 ? first : "%s 외 %d건".formatted(first, items.size() - 1);
    }
}
