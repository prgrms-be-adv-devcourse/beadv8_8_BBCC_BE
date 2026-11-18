package com.bbcc.kidly.boundedcontext.notification.app;

public record SuspendedEmail(String title, String body) implements EmailContent {

    private static final String TITLE = "[Kidly] 계정 이용이 정지됐어요";
    private static final String BODY = "관리자가 회원님의 계정 이용을 정지했어요.\n";
    private static final String REASON = "\n정지 사유: %s\n";

    public static SuspendedEmail compose(String reason) {
        var hasReason = reason != null && !reason.isBlank();
        return new SuspendedEmail(TITLE, hasReason ? BODY + REASON.formatted(reason.strip()) : BODY);
    }
}
