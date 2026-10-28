package com.bbcc.kidly.boundedcontext.notification.app.port;

import org.springframework.util.StringUtils;

/**
 * 보낼 알림 메일 한 통 (일반 텍스트).
 *
 * @param to      받는 사람 주소
 * @param subject 제목
 * @param text    본문
 */
public record Email(String to, String subject, String text) {

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
