package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentDetail;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentSummary;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentTotals;
import com.bbcc.kidly.boundedcontext.payment.domain.DailyPaymentTotal;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 주문 결제 내역을 조회한다. 남의 결제는 없는 결제처럼 404다. 관리자는 모든 회원의 결제를 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    static final int MAX_SUMMARY_DAYS = 31;

    private final PaymentRepository paymentRepository;

    /**
     * 내 결제를 최신순으로 준다. orderId를 주면 그 주문의 내 결제만(주문당 하나라 0 또는 1건) 준다.
     */
    public Page<PaymentSummary> getPayments(Long memberId, Long orderId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Payment> payments = orderId == null
            ? paymentRepository.findAllByMemberIdOrderByIdDesc(memberId, pageable)
            : paymentRepository.findAllByMemberIdAndOrderIdOrderByIdDesc(memberId, orderId, pageable);
        return payments.map(PaymentSummary::from);
    }

    public Page<PaymentSummary> getAllPayments(int page, int size) {
        return paymentRepository.findAllByOrderByIdDesc(PageRequest.of(page, size)).map(PaymentSummary::from);
    }

    public PaymentDetail getPayment(Long memberId, Long paymentId) {
        return paymentRepository.findById(paymentId)
            .filter(payment -> payment.getMemberId().equals(memberId))
            .map(PaymentDetail::from)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    /**
     * 결제일 기준 [from, to] 날짜별 결제 금액 · 건수와 환불 이력이 있는 결제 전체 건수를 준다.
     * 날짜는 결제 시각(한국 시간)의 날짜다.
     */
    public PaymentTotals getTotals(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > MAX_SUMMARY_DAYS) {
            throw new BusinessException(PaymentErrorCode.INVALID_SUMMARY_PERIOD);
        }
        Map<LocalDate, DailyPaymentTotal> paid = paymentRepository
            .sumDailyPaidBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
            .collect(Collectors.toMap(DailyPaymentTotal::date, Function.identity()));
        List<PaymentTotals.Day> daily = from.datesUntil(to.plusDays(1))
            .map(date -> {
                DailyPaymentTotal total = paid.get(date);
                return total == null ? new PaymentTotals.Day(date, 0, 0)
                    : new PaymentTotals.Day(date, total.amount(), total.count());
            })
            .toList();
        long refunded = paymentRepository.countByStatusIn(
            EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED));
        return new PaymentTotals(daily, refunded);
    }
}
