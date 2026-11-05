package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 환불 완료 메일. 주문 취소로 예치금에 돌려준 금액과 주문 상세 주소를 알린다.
 */
public record RefundEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 환불이 완료됐어요";
    private static final String BODY = """
        취소한 주문의 %,d원을 예치금으로 돌려드렸어요.

        주문 상세 보기: %s/orders/%d
        """;

    public static RefundEmail compose(String siteUrl, Long orderId, long amount) {
        return new RefundEmail(TITLE, BODY.formatted(amount, siteUrl, orderId));
    }
}
