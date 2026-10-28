package com.bbcc.kidly.boundedcontext.payment.app.dto;

/**
 * 정산금액 입금 요청.
 *
 * @param settlementId 정산서 ID. 지급 기록과 원장 SETTLEMENT의 ref_id로 남겨 같은 정산서를 한 번만 입금한다
 * @param sellerId 판매자 ID(회원 ID)
 * @param amount 정산금액(원). 0이면 입금하지 않는다
 */
public record PayoutCommand(Long settlementId, Long sellerId, long amount) {
}
