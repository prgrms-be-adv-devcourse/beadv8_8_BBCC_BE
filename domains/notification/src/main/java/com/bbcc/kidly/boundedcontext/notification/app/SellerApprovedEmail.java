package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 판매자 승인 메일. 결과를 기다리는 신청자에게 승인 사실과 판매자 센터 주소를 알린다.
 */
public record SellerApprovedEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 판매자 신청이 승인됐어요";
    private static final String BODY = """
        보내 주신 판매자 신청이 승인됐어요.

        상호명: %s
        판매자 센터: %s/center
        """;

    public static SellerApprovedEmail compose(String siteUrl, String businessName) {
        return new SellerApprovedEmail(TITLE, BODY.formatted(businessName, siteUrl));
    }
}
