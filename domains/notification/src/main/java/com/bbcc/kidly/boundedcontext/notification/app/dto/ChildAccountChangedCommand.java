package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 자녀 계정이 만들어지거나(deleted = false) 지워졌다(deleted = true).
 *
 * @param accountId 자녀 계정 ID. 자녀 알림함의 받는 사람 ID다
 * @param childProfileId 자녀 프로필 ID. 조르기 이벤트의 childId다
 */
public record ChildAccountChangedCommand(Long accountId, Long childProfileId, boolean deleted) {
}
