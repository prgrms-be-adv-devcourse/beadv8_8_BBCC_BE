package com.bbcc.kidly.boundedcontext.notification.app.port;

import org.springframework.util.StringUtils;

public record Email(String to, String subject, String text) implements OutgoingMessage {

    public Email {
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("받는 사람이 없습니다");
        }
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("제목이 없습니다");
        }
        if (!StringUtils.hasLength(text)) {
            throw new IllegalArgumentException("본문이 없습니다");
        }
    }
}
