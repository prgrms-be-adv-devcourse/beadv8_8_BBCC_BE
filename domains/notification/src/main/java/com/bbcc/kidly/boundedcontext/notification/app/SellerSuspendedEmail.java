package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 판매자 정지 메일. 갑자기 판매자 센터를 쓸 수 없게 된 판매자에게 정지 사실을 알린다.
 * 판매자 변경 이벤트에는 정지 사유가 없어 넣지 않는다.
 */
public record SellerSuspendedEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 판매자 자격이 정지됐어요";
    private static final String BODY = "관리자가 %s 상호의 판매자 자격을 정지했어요.\n";

    public static SellerSuspendedEmail compose(String businessName) {
        return new SellerSuspendedEmail(TITLE, BODY.formatted(businessName));
    }
}
