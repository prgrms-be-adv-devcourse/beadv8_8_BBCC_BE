package com.bbcc.kidly.boundedcontext.payment.app.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 결제 요약.
 *
 * @param daily 조회 기간의 모든 날짜를 오름차순으로. 결제가 없는 날은 0이다
 * @param refundedPaymentCount 기간과 상관없이 환불 이력이 있는 결제(부분 · 전액 환불) 전체 건수
 */
public record PaymentTotals(List<Day> daily, long refundedPaymentCount) {

    /**
     * @param amount 그날 결제된 금액(환불 전, 실패 결제 제외)
     * @param count 그날 결제된 건수
     */
    public record Day(LocalDate date, long amount, long count) {
    }
}
