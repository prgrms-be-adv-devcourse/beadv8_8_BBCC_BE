package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentQueryService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.AdminPaymentResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 전체 결제 · 환불 조회. /api/v1/admin/**라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    public PageResponse<AdminPaymentResponse> getPayments(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(paymentQueryService.getAllPayments(page, size).map(AdminPaymentResponse::from));
    }
}
