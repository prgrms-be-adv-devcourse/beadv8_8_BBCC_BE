package com.bbcc.kidly.boundedcontext.order.api;

/**
 * 자녀 복사본(#617).
 *
 * @param memberId 보호자 회원 ID
 */
public record ChildView(Long childId, Long memberId, boolean deleted) {
}
