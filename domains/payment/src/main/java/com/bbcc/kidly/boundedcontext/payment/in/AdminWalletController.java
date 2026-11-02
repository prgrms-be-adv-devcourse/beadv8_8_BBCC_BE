package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.WalletService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.AdminWalletResponse;
import com.bbcc.kidly.global.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 예치금 지갑 조회(#670). /api/v1/admin/**라 ADMIN만 호출할 수 있다.
 * 탈퇴한 회원의 남은 잔액을 환급하려고 {@code ?frozen=true&hasBalance=true}로 동결됐고 잔액이 남은 지갑을 본다.
 */
@RestController
@RequestMapping("/api/v1/admin/wallets")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    /**
     * @param frozen true면 동결된 지갑만 준다. false면 동결 여부로 거르지 않는다
     * @param hasBalance true면 잔액이 0보다 큰 지갑만 준다. false면 잔액으로 거르지 않는다
     */
    @GetMapping
    public PageResponse<AdminWalletResponse> getWallets(
        @RequestParam(defaultValue = "false") boolean frozen,
        @RequestParam(defaultValue = "false") boolean hasBalance,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(walletService.getWallets(frozen, hasBalance, page, size)
            .map(AdminWalletResponse::from));
    }
}
