package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentQueryService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.PaymentDetailResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.PaymentSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 주문 결제 내역.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    public PageResponse<PaymentSummaryResponse> getPayments(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(
            paymentQueryService.getPayments(member.id(), page, size).map(PaymentSummaryResponse::from));
    }

    @GetMapping("/{paymentId}")
    public PaymentDetailResponse getPayment(@LoginMember AuthMember member, @PathVariable Long paymentId) {
        return PaymentDetailResponse.from(paymentQueryService.getPayment(member.id(), paymentId));
    }
}
