package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentDetail;
import com.bbcc.kidly.boundedcontext.payment.app.dto.PaymentSummary;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 주문 결제 내역을 조회한다. 남의 결제는 없는 결제처럼 404다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public Page<PaymentSummary> getPayments(Long memberId, int page, int size) {
        return paymentRepository.findAllByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size))
            .map(PaymentSummary::from);
    }

    public PaymentDetail getPayment(Long memberId, Long paymentId) {
        return paymentRepository.findById(paymentId)
            .filter(payment -> payment.getMemberId().equals(memberId))
            .map(PaymentDetail::from)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}
