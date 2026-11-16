package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.PaymentOutboxParkingService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.ParkedPaymentEventResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 격리된 결제 이벤트 조회 · 다시 보내기(#1389). /api/v1/admin/**라 관리자만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/payments/parked-events")
@RequiredArgsConstructor
public class AdminPaymentOutboxController {

    private final PaymentOutboxParkingService parkingService;

    // 최신 격리순
    @GetMapping
    public PageResponse<ParkedPaymentEventResponse> getParked(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(parkingService.getParked(page, size).map(ParkedPaymentEventResponse::from));
    }

    /**
     * 격리를 풀어 다음 릴레이가 이 메시지부터 같은 키의 뒤 메시지까지 순서대로 보내게 한다. 시도 상한은 처음부터 다시 센다.
     * 격리되지 않은 메시지면 409다.
     */
    @PostMapping("/{outboxId}/release")
    public ParkedPaymentEventResponse release(@LoginAdmin AuthAdmin admin, @PathVariable Long outboxId) {
        return ParkedPaymentEventResponse.from(parkingService.release(outboxId, admin.id()));
    }
}
