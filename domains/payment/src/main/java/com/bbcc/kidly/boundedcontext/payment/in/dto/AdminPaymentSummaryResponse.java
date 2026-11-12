package com.bbcc.kidly.boundedcontext.payment.in.dto;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentTotals;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 결제 요약.
 *
 * @param daily 조회 기간의 날짜마다 한 줄(오름차순). 결제가 없는 날은 0
 * @param refundedPaymentCount 환불 이력이 있는 결제(부분 · 전액 환불) 전체 건수. 기간과 상관없다
 */
public record AdminPaymentSummaryResponse(List<Day> daily, long refundedPaymentCount) {

    /**
     * @param paidAmount 그날 결제된 금액(환불 전, 실패 결제 제외)
     * @param paidCount 그날 결제된 건수
     */
    public record Day(LocalDate date, long paidAmount, long paidCount) {
    }

    public static AdminPaymentSummaryResponse from(PaymentTotals totals) {
        return new AdminPaymentSummaryResponse(
            totals.daily().stream().map(day -> new Day(day.date(), day.amount(), day.count())).toList(),
            totals.refundedPaymentCount());
    }
}
