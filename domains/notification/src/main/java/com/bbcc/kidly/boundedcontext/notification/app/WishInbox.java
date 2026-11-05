package com.bbcc.kidly.boundedcontext.notification.app;

import java.util.Optional;

/**
 * 조르기 알림함 문구. 첫 상품 이름과 나머지 개수를 보여 준다. 이름을 모르면 개수만 쓴다.
 * 상품 이름은 상품 서비스가 255자까지 받으므로 본문 한도(1000자)를 넘지 않는다.
 */
public record WishInbox(String title, String body) {

    private static final String TITLE = "아이가 구매 요청을 보냈어요";

    public static WishInbox compose(Optional<String> firstProductName, int itemCount) {
        var body = firstProductName
            .map(name -> itemCount > 1 ? "조른 상품: %s 외 %d개".formatted(name, itemCount - 1) : "조른 상품: " + name)
            .orElse("조른 상품 %d개".formatted(itemCount));
        return new WishInbox(TITLE, body);
    }
}
