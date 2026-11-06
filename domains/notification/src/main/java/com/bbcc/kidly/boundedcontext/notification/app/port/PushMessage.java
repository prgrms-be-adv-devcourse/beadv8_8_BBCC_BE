package com.bbcc.kidly.boundedcontext.notification.app.port;

import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 한 회원의 모든 브라우저에 한 번에 보낼 웹 푸시. 짧은 제목 · 본문과 누르면 열 주소만 담는다.
 *
 * @param fids 받는 브라우저의 FID(Firebase Installation ID). 비어 있지 않다
 * @param link 누르면 열 사이트 주소(절대 주소)
 */
public record PushMessage(List<String> fids, String title, String body, String link) implements OutgoingMessage {

    public PushMessage {
        if (fids == null || fids.isEmpty()) {
            throw new IllegalArgumentException("받을 FID가 없습니다");
        }
        if (!StringUtils.hasText(title) || !StringUtils.hasText(body)) {
            throw new IllegalArgumentException("제목 · 본문이 없습니다");
        }
        fids = List.copyOf(fids);
    }

    // FID는 브라우저 식별값이라 로그에 남지 않게 개수만 보인다
    @Override
    public String toString() {
        return "PushMessage[fids=%d, title=%s]".formatted(fids.size(), title);
    }
}
