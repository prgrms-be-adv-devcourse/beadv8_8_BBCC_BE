package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SellerSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementTargetResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 정산 API. /api/v1/seller/** 라 SELLER만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
public class SellerSettlementController {

    private final SellerSettlementService sellerSettlementService;

    // 아직 정산서에 들어가지 않은 본인의 구매확정 항목. 정렬은 확정 시각 최신순 고정
    @GetMapping("/settlement-targets")
    public PageResponse<SettlementTargetResponse> targets(
        @LoginMember AuthMember seller, @PageableDefault Pageable pageable) {
        return PageResponse.from(
            sellerSettlementService.getTargets(seller.id(), pageable).map(SettlementTargetResponse::from));
    }
}
