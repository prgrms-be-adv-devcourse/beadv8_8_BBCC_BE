/**
 * 알림 도메인: 다른 도메인의 이벤트를 받아 알림을 저장하고 채널(이메일)로 보낸다. DB 스키마 notification.
 * 회원이 사이트에서 보는 알림함(inbox)도 둔다(조회 · 안 읽은 개수 · 읽음 API).
 * 이벤트를 받기만 하고 발행하지 않는다.
 */
package com.bbcc.kidly.boundedcontext.notification;
