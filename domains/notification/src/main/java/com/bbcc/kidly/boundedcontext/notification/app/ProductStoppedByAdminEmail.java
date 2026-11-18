package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 상품 변경 이벤트에는 중지 사유가 없어 넣지 않는다.
 */
public record ProductStoppedByAdminEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 상품 판매가 중지됐어요";
    private static final String BODY = """
        관리자가 운영 정책에 따라 %s 상품의 판매를 중지했어요.
        관리자가 중지한 상품은 판매자 센터에서 다시 판매를 시작할 수 없어요. 궁금한 점은 고객센터로 문의해 주세요.

        상품 보기: %s/center/products/%d
        """;

    public static ProductStoppedByAdminEmail compose(String siteUrl, Long productId, String productName) {
        return new ProductStoppedByAdminEmail(TITLE, BODY.formatted(productName, siteUrl, productId));
    }
}
