package com.bbcc.kidly.boundedcontext.notification.app;

import java.util.Optional;

/**
 * 자녀에게 보내는 조르기 결과(결제 완료 · 거절 · 만료) 알림 문구. 아이가 읽으므로 쉬운 말로 쓴다.
 * 본문은 결과 한 문장과 상품(첫 상품 이름 · 나머지 개수, 이름을 모르면 개수만)이다.
 * 모르는 거절 사유(나중에 늘어난 값 · 없음)는 기타로 쓴다. 상품 이름은 255자까지라 본문 한도(1000자)를 넘지 않는다.
 */
public record ChildWishInbox(String title, String body) {

    public static ChildWishInbox paid(Optional<String> firstProductName, int itemCount) {
        return new ChildWishInbox("조르기를 들어줬어요!",
            "부모님이 결제했어요. " + products("조른 상품", firstProductName, itemCount));
    }

    public static ChildWishInbox rejected(String reason, Optional<String> firstProductName, int itemCount) {
        return new ChildWishInbox("이번 조르기는 들어주기 어렵대요",
            reasonText(reason) + " " + products("조른 상품", firstProductName, itemCount));
    }

    public static ChildWishInbox expired(Optional<String> firstProductName, int itemCount) {
        return new ChildWishInbox("조르기 기한이 지났어요",
            "부모님 답을 받지 못하고 기한이 지났어요. " + products("조른 상품", firstProductName, itemCount));
    }

    private static String products(String label, Optional<String> firstProductName, int itemCount) {
        return firstProductName
            .map(name -> itemCount > 1 ? "%s: %s 외 %d개".formatted(label, name, itemCount - 1) : label + ": " + name)
            .orElse("%s %d개".formatted(label, itemCount));
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
