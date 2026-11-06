package com.bbcc.kidly.boundedcontext.order.app.replica.dto;

import java.time.LocalDateTime;

/**
 * 자녀 계정 생성 · 삭제를 복사본에 반영하는 입력(#906).
 *
 * @param deleted 계정이 지워졌으면 true
 */
public record ChildAccountChangedCommand(
    Long accountId,
    Long childProfileId,
    Long guardianId,
    boolean deleted,
    LocalDateTime occurredAt) {
}
