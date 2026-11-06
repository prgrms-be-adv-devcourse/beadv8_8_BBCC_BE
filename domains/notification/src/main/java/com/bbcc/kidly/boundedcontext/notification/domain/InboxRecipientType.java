package com.bbcc.kidly.boundedcontext.notification.domain;

/**
 * 알림함 알림을 받는 로그인 주체의 종류. 회원 ID와 자녀 계정 ID는 ID 공간이 달라 같은 숫자일 수 있으므로,
 * 알림은 항상 종류와 ID를 함께 저장하고 둘 다로 거른다.
 */
public enum InboxRecipientType {
    MEMBER,
    CHILD
}
