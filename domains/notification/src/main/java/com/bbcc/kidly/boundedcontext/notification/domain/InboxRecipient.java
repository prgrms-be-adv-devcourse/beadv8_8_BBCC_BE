package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.shared.auth.PrincipalId;
import org.springframework.util.Assert;

/**
 * 알림함 알림을 받는 로그인 주체. 회원이면 회원 ID, 자녀면 자녀 계정 ID다(자녀 프로필 ID가 아니다).
 * 알림을 넣고 · 보고 · 읽고 · 지울 때 종류와 ID를 늘 함께 넘겨, 같은 숫자 ID의 다른 주체 알림이 섞이지 않게 한다.
 */
public record InboxRecipient(InboxRecipientType type, Long id) {

    public InboxRecipient {
        Assert.notNull(type, "알림함 받는 사람의 종류는 비어 있을 수 없습니다");
        Assert.notNull(id, "알림함 받는 사람의 ID는 비어 있을 수 없습니다");
    }

    public static InboxRecipient member(Long memberId) {
        return new InboxRecipient(InboxRecipientType.MEMBER, memberId);
    }

    public static InboxRecipient child(Long childAccountId) {
        return new InboxRecipient(InboxRecipientType.CHILD, childAccountId);
    }

    /**
     * 로그인 주체의 알림함. 관리자는 알림함이 없다(IllegalArgumentException).
     */
    public static InboxRecipient of(PrincipalId principal) {
        return switch (principal.type()) {
            case MEMBER -> member(principal.id());
            case CHILD -> child(principal.id());
            case ADMIN -> throw new IllegalArgumentException("관리자는 알림함이 없습니다");
        };
    }
}
