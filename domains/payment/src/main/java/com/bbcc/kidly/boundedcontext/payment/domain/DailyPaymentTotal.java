package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDate;

/**
 * 결제일(paidAt) 하루의 결제 합계. 실패 결제(paidAt 없음)는 들어가지 않고, 금액은 환불 전 결제 금액이다.
 */
public record DailyPaymentTotal(LocalDate date, long amount, long count) {
}
