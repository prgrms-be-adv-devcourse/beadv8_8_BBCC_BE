package com.bbcc.kidly.boundedcontext.notification.app;

public record PayoutEmail(String title, String body) implements EmailContent {

    private static final String TITLE = "[Kidly] 정산금이 입금됐어요";
    private static final String BODY = """
        정산금 %,d원이 예치금으로 입금됐어요.

        정산 상세 보기: %s/center/settlements/%d
        """;

    public static PayoutEmail compose(String siteUrl, Long settlementId, long amount) {
        return new PayoutEmail(TITLE, BODY.formatted(amount, siteUrl, settlementId));
    }
}
