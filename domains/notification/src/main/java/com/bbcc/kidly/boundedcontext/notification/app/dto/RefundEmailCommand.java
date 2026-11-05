package com.bbcc.kidly.boundedcontext.notification.app.dto;

import java.util.UUID;

/**
 * 환불 완료 메일을 만들 환불 결과.
 *
 * @param eventId 환불 이벤트 ID. 같은 이벤트로는 한 통만 만든다
 * @param memberId 환불받은 회원 ID. 이 필드를 더하기 전의 이벤트에는 없다(null)
 * @param amount 총 환불 금액(원)
 */
public record RefundEmailCommand(UUID eventId, Long memberId, Long orderId, long amount) {
}
