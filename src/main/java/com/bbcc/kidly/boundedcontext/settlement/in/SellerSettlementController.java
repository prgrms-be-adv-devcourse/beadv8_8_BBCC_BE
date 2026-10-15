package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SellerSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementSearchRequest;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementDetailResponse;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementSummaryResponse;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementTargetResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    // 받은 정산서. 기간은 정산 종료일 기준이고 정렬은 종료일 최신순 고정
    @GetMapping("/settlements")
    public PageResponse<SettlementSummaryResponse> settlements(
        @LoginMember AuthMember seller, @Valid @ModelAttribute SettlementSearchRequest request,
        @PageableDefault Pageable pageable) {
        return PageResponse.from(
            sellerSettlementService.getSettlements(seller.id(), request, pageable)
                .map(SettlementSummaryResponse::from));
    }

    @GetMapping("/settlements/{settlementId}")
    public SettlementDetailResponse settlement(@LoginMember AuthMember seller, @PathVariable Long settlementId) {
        return SettlementDetailResponse.from(sellerSettlementService.getSettlement(seller.id(), settlementId));
    }
}
