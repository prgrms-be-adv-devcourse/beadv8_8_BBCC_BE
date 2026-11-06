package com.bbcc.kidly.boundedcontext.notification.domain;

/**
 * 알림함에 넣을 알림 한 건의 값. recipient는 받는 로그인 주체(회원 또는 자녀 계정)다. 제목 · 본문은 완성된 문구다.
 * dedupKey는 같은 일로 두 번 넣지 않기 위한 키다(예: 받은 이벤트 ID와 유형). linkPath는 없으면 null.
 */
public record InboxNotificationInfo(
    InboxRecipient recipient,
    NotificationType type,
    String title,
    String body,
    String linkPath,
    String dedupKey
) {
}
