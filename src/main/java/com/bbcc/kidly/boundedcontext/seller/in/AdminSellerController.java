package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 판매자 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final SellerService sellerService;

    // 정지된 판매자를 돌려준다. 동시 정지는 @Version 충돌로 409
    @PostMapping("/{sellerId}/suspend")
    public SellerResponse suspend(@LoginMember AuthMember admin, @PathVariable Long sellerId,
        @Valid @RequestBody SellerSuspendRequest request) {
        return SellerResponse.from(sellerService.suspend(sellerId, admin.id(), request));
    }
}
