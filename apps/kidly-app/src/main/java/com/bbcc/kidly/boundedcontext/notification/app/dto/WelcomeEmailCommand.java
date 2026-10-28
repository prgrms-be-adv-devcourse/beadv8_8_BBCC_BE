package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.UUID;

/**
 * 환영 메일을 만들 가입 정보.
 *
 * @param eventId 가입 이벤트 ID. 같은 이벤트로는 한 통만 만든다
 * @param email 가입 때 받은 이메일. 이메일을 받기 전에 가입한 회원은 null
 */
public record WelcomeEmailCommand(UUID eventId, Long memberId, String email) {
}
