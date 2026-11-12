package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.shared.auth.PrincipalId;

/**
 * 이 로그인 주체(받는 사람)의 안 읽은 알림 수가 바뀌었다(알림을 넣음 · 읽음 처리). 같은 앱 안에서만 쓰는 Spring 이벤트다.
 */
public record InboxChanged(PrincipalId principal) {

    static InboxChanged of(InboxRecipient recipient) {
        return new InboxChanged(switch (recipient.type()) {
            case MEMBER -> PrincipalId.member(recipient.id());
            case CHILD -> PrincipalId.child(recipient.id());
        });
    }
}
