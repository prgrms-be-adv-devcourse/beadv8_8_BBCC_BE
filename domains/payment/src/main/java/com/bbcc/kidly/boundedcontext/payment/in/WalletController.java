package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.WalletService;
import com.bbcc.kidly.boundedcontext.payment.in.dto.WalletBalanceResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.WalletLedgerResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 예치금 지갑 API.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public WalletBalanceResponse getMyBalance(@LoginMember AuthMember member) {
        return WalletBalanceResponse.from(walletService.getBalance(member.id()));
    }

    /**
     * 내 입출금 내역(원장). 최신순이고, 입금은 양수 · 출금은 음수 금액이다.
     */
    @GetMapping("/me/ledgers")
    public PageResponse<WalletLedgerResponse> getMyLedgers(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(walletService.getLedgers(member.id(), page, size).map(WalletLedgerResponse::from));
    }
}
