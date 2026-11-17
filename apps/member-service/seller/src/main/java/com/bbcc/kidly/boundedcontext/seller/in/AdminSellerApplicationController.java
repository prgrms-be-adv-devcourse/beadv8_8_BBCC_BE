package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerApplicationService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationSummaryResponse;
import com.bbcc.kidly.member.support.response.PageResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
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

@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@RequiredArgsConstructor
public class AdminSellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    @GetMapping
    public PageResponse<SellerApplicationSummaryResponse> list(
        @RequestParam(required = false) SellerApplicationStatus status,
        @PageableDefault Pageable pageable) {
        return PageResponse.from(
            sellerApplicationService.getApplications(status, pageable).map(SellerApplicationSummaryResponse::from));
    }

    @PostMapping("/{applicationId}/approve")
    public SellerApplicationSummaryResponse approve(@LoginAdmin AuthAdmin admin, @PathVariable Long applicationId) {
        return SellerApplicationSummaryResponse.from(sellerApplicationService.approve(applicationId, admin.id()));
    }

    @PostMapping("/{applicationId}/reject")
    public SellerApplicationSummaryResponse reject(@LoginAdmin AuthAdmin admin, @PathVariable Long applicationId,
        @Valid @RequestBody SellerApplicationRejectRequest request) {
        return SellerApplicationSummaryResponse.from(
            sellerApplicationService.reject(applicationId, admin.id(), request));
    }
}
