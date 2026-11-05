package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 판매자 신청 거절 메일. 결과를 기다리는 신청자에게 거절 사실과 사유를 알린다. 사유가 없으면 사유 줄을 넣지 않는다.
 */
public record ApplicationRejectedEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 판매자 신청이 거절됐어요";
    private static final String BODY = "보내 주신 판매자 신청이 거절됐어요.\n";
    private static final String REASON = "\n거절 사유: %s\n";

    public static ApplicationRejectedEmail compose(String reason) {
        var hasReason = reason != null && !reason.isBlank();
        return new ApplicationRejectedEmail(TITLE, hasReason ? BODY + REASON.formatted(reason.strip()) : BODY);
    }
}
