package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentQueryService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.AdminPaymentResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.AdminPaymentSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    public PageResponse<AdminPaymentResponse> getPayments(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(paymentQueryService.getAllPayments(page, size).map(AdminPaymentResponse::from));
    }

    /**
     * 결제일 기준 날짜별 결제 금액 · 건수와 환불 이력이 있는 결제 수. 날짜는 한국 시간이고 from · to를 모두 포함한다.
     * 둘 다 비우면 오늘, to만 비우면 오늘까지, from만 비우면 to 하루다. 한 번에 31일까지.
     */
    @GetMapping("/summary")
    public AdminPaymentSummaryResponse getSummary(
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(KOREA);
        LocalDate start = from != null ? from : end;
        return AdminPaymentSummaryResponse.from(paymentQueryService.getTotals(start, end));
    }
}
