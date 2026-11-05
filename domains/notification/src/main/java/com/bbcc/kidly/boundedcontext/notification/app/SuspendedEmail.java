package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 계정 정지 메일. 갑자기 로그인이 막힌 회원에게 정지 사실과 사유를 알린다. 사유가 없으면 사유 줄을 넣지 않는다.
 */
public record SuspendedEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 계정 이용이 정지됐어요";
    private static final String BODY = "관리자가 회원님의 계정 이용을 정지했어요.\n";
    private static final String REASON = "\n정지 사유: %s\n";

    public static SuspendedEmail compose(String reason) {
        var hasReason = reason != null && !reason.isBlank();
        return new SuspendedEmail(TITLE, hasReason ? BODY + REASON.formatted(reason.strip()) : BODY);
    }
}
