package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.StuckChargeService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.AdminStuckChargeResponse;
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
 * 관리자의 멈춘 충전 조회 · 종료(#1392). Gateway가 결제 서비스로 넘기는 관리자 경로가
 * /api/v1/admin/payments/** · /api/v1/admin/wallets/**뿐이라 지갑 아래에 둔다.
 */
@RestController
@RequestMapping("/api/v1/admin/wallets/charges")
@RequiredArgsConstructor
public class AdminChargeController {

    private final StuckChargeService stuckChargeService;

    @GetMapping("/stuck")
    public PageResponse<AdminStuckChargeResponse> getStuck(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(stuckChargeService.getStuck(page, size).map(AdminStuckChargeResponse::from));
    }

    /**
     * 운영자가 토스 상점관리자에서 돈을 정리한 멈춘 충전을 실패로 끝낸다. 예치금은 바꾸지 않는다.
     */
    @PostMapping("/{chargeId}/fail")
    public AdminStuckChargeResponse fail(@LoginAdmin AuthAdmin admin, @PathVariable Long chargeId) {
        return AdminStuckChargeResponse.from(stuckChargeService.close(chargeId, admin.id()));
    }
}
