package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 판매자 신청 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@RequiredArgsConstructor
public class AdminSellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    // status가 없으면 전체. "심사 중" 배지 숫자는 status=PENDING의 totalElements로 계산한다
    @GetMapping
    public PageResponse<SellerApplicationSummaryResponse> list(
        @RequestParam(required = false) SellerApplicationStatus status,
        @PageableDefault Pageable pageable) {
        return PageResponse.from(
            sellerApplicationService.getApplications(status, pageable).map(SellerApplicationSummaryResponse::from));
    }

    // 승인된 행을 화면이 바로 갱신할 수 있게 그 행을 돌려준다
    @PostMapping("/{applicationId}/approve")
    public SellerApplicationSummaryResponse approve(@LoginMember AuthMember admin, @PathVariable Long applicationId) {
        return SellerApplicationSummaryResponse.from(sellerApplicationService.approve(applicationId, admin.id()));
    }

    // 승인과 같은 모양으로 거절된 행을 돌려준다
    @PostMapping("/{applicationId}/reject")
    public SellerApplicationSummaryResponse reject(@LoginMember AuthMember admin, @PathVariable Long applicationId,
        @Valid @RequestBody SellerApplicationRejectRequest request) {
        return SellerApplicationSummaryResponse.from(
            sellerApplicationService.reject(applicationId, admin.id(), request));
    }
}
