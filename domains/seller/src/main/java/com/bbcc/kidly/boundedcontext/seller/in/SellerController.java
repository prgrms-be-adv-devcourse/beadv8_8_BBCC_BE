package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 판매자의 정보 API. SELLER 권한은 SecurityConfig가 확인한다.
 */
@RestController
@RequestMapping("/api/v1/seller/me")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    // 판매자 ID는 회원 ID와 같다. 판매자 정보가 없으면 404
    @GetMapping
    public SellerResponse getMine(@LoginMember AuthMember member) {
        return SellerResponse.from(sellerService.getMine(member.id()));
    }
}
