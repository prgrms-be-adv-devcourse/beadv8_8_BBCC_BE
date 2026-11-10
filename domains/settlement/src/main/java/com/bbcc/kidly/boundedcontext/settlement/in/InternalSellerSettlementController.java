package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SellerSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SellerWithdrawalBlockersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서비스가 부르는 내부 정산 API. Gateway가 /internal/**을 밖에서 막으므로 인증 헤더를 받지 않는다.
 */
@RestController
@RequestMapping("/internal/sellers")
@RequiredArgsConstructor
public class InternalSellerSettlementController {

    private final SellerSettlementService sellerSettlementService;

    // 회원 서비스가 판매자 탈퇴 전에 부른다(#1091). 정산은 판매자 원본이 없으므로 모르는 판매자도 빈 목록이다
    @GetMapping("/{sellerId}/withdrawal-blockers")
    public SellerWithdrawalBlockersResponse withdrawalBlockers(@PathVariable Long sellerId) {
        return SellerWithdrawalBlockersResponse.from(sellerSettlementService.getWithdrawalBlockers(sellerId));
    }
}
