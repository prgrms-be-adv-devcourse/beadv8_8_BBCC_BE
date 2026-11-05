package com.bbcc.kidly.boundedcontext.notification.app.dto;

/**
 * 정산금 지급 메일을 만들 지급 결과.
 *
 * @param sellerId 판매자 ID(회원 ID). 이 회원의 이메일 복사본으로 보낸다
 * @param amount 입금한 금액(원)
 */
public record PayoutEmailCommand(Long settlementId, Long sellerId, long amount) {
}
