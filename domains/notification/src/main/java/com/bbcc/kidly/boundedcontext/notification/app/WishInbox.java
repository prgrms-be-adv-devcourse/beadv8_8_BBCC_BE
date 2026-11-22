package com.bbcc.kidly.boundedcontext.notification.app;

import java.util.Optional;

/**
 * 자녀에게 가는 문구는 아이가 읽으므로 쉬운 말로 쓴다. 모르는 거절 사유(나중에 늘어난 값, 없음)는 기타로 쓴다.
 * 상품 이름은 상품 서비스가 255자까지 받으므로 본문 한도(1000자)를 넘지 않는다.
 */
public record WishInbox(String title, String body) {

    public static WishInbox sent(Optional<String> firstProductName, int itemCount) {
        return new WishInbox("아이가 구매 요청을 보냈어요", products(firstProductName, itemCount));
    }

    public static WishInbox paid(Optional<String> firstProductName, int itemCount) {
        return new WishInbox("조르기를 들어줬어요!",
            "부모님이 결제했어요. " + products(firstProductName, itemCount));
    }

    public static WishInbox rejected(String reason, Optional<String> firstProductName, int itemCount) {
        return new WishInbox("이번 조르기는 들어주기 어렵대요",
            reasonText(reason) + " " + products(firstProductName, itemCount));
    }

    public static WishInbox expired(Optional<String> firstProductName, int itemCount) {
        return new WishInbox("조르기 기한이 지났어요",
            "부모님 답을 받지 못하고 기한이 지났어요. " + products(firstProductName, itemCount));
    }

    private static String products(Optional<String> firstProductName, int itemCount) {
        return firstProductName
            .map(name -> itemCount > 1 ? "조른 상품: %s 외 %d개".formatted(name, itemCount - 1) : "조른 상품: " + name)
            .orElse("조른 상품 %d개".formatted(itemCount));
    }

    private static String reasonText(String reason) {
        return switch (reason == null ? "" : reason) {
            case "TOO_EXPENSIVE" -> "지금 사기에는 가격이 많이 비싸대요.";
            case "ALREADY_HAVE" -> "비슷한 물건을 이미 가지고 있대요.";
            case "NEXT_TIME" -> "다음에 다시 이야기해 보재요.";
            default -> "자세한 이유는 부모님께 물어봐요.";
        };
    }
}
