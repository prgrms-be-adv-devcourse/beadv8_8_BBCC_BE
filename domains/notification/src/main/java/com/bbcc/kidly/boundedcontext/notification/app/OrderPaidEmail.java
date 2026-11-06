package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.OrderPaidCommand;
import java.util.stream.Collectors;

/**
 * 주문 확인 메일. 결제가 끝난 주문의 상품 · 수량 · 금액과 주문 상세 주소를 알린다.
 * 상품명 · 옵션명은 주문할 때의 스냅샷이다. 옵션명이 없는 옛 주문상품은 옵션을 쓰지 않는다.
 * 주문상품 수에 상한이 없어, 본문 한도(4000자)를 넘지 않도록 상품은 앞의 5개까지만 적고 나머지는 "외 N건"으로 줄인다
 * (상품명 · 옵션명은 각각 255자까지라 한 줄이 600자를 넘지 않는다).
 */
public record OrderPaidEmail(String title, String body) {

    private static final int MAX_LINES = 5;
    private static final String TITLE = "[Kidly] 주문이 결제됐어요";
    private static final String BODY = """
        주문하신 상품의 결제가 끝났어요.

        %s

        총 결제 금액: %,d원
        주문 상세 보기: %s/orders/%d
        """;

    public static OrderPaidEmail compose(String siteUrl, OrderPaidCommand command) {
        var items = command.items();
        var lines = items.stream().limit(MAX_LINES).map(OrderPaidEmail::line).collect(Collectors.joining("\n"));
        if (items.size() > MAX_LINES) {
            lines += "\n- 외 %d건".formatted(items.size() - MAX_LINES);
        }
        return new OrderPaidEmail(TITLE, BODY.formatted(lines, command.totalAmount(), siteUrl, command.orderId()));
    }

    private static String line(OrderPaidCommand.Item item) {
        var option = item.optionName() == null ? "" : " (" + item.optionName() + ")";
        return "- %s%s × %d: %,d원".formatted(item.productName(), option, item.quantity(), item.amount());
    }
}
