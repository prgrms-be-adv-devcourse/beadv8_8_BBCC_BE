package com.bbcc.kidly.boundedcontext.payment.app;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 결제 행동 로그의 action. 예치금 · 충전 · 결제 상태가 바뀐 일이다. 조회는 남기지 않는다.
 * description은 사람이 읽기 쉽게 로그 한 줄 맨 앞에 쓰는 한글 설명이다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PaymentAction {
    CHARGE_REQUESTED("충전 요청"),
    CHARGE_CONFIRM_STARTED("충전 승인 시작"),
    CHARGE_CONFIRMED("충전 승인"),
    CHARGE_FAILED("충전 실패"),
    CHARGE_EXPIRED("충전 만료"),
    CHARGE_CANCEL_REQUESTED("충전 취소 요청"),
    CHARGE_CANCELLED("충전 취소 완료"),
    CHARGE_CANCEL_FAILED("충전 취소 거절"),
    CHARGE_CANCELLED_EXTERNALLY("토스 직접 취소 반영"),
    ORDER_PAID("주문 결제 완료"),
    ORDER_PAYMENT_FAILED("주문 결제 실패"),
    ORDER_CANCEL_RECORDED("결제 전 주문 취소 기록"),
    ORDER_REFUNDED("주문 취소 환불"),
    PAYOUT_COMPLETED("정산 지급 완료"),
    WALLET_FROZEN("탈퇴 회원 지갑 동결");

    private final String description;
}
