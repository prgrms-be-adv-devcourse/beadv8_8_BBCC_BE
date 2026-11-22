package com.bbcc.kidly.boundedcontext.notification.app;

import org.springframework.util.StringUtils;

public record ApplicationRejectedEmail(String title, String body) implements EmailContent {

    private static final String TITLE = "[Kidly] 판매자 신청이 거절됐어요";
    private static final String BODY = "보내 주신 판매자 신청이 거절됐어요.\n";
    private static final String REASON = "\n거절 사유: %s\n";

    public static ApplicationRejectedEmail compose(String reason) {
        return new ApplicationRejectedEmail(TITLE,
            StringUtils.hasText(reason) ? BODY + REASON.formatted(reason.strip()) : BODY);
    }
}
