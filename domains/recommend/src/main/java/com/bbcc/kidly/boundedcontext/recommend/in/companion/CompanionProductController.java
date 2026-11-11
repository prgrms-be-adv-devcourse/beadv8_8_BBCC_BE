package com.bbcc.kidly.boundedcontext.recommend.in.companion;

import com.bbcc.kidly.boundedcontext.recommend.app.companion.CompanionProductService;
import com.bbcc.kidly.boundedcontext.recommend.in.companion.dto.CompanionProductsResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 상세에서 고른 자녀에게 맞는 사이즈로, 보고 있는 상품과 함께 사면 좋은 상품을 4개까지 돌려주는 API다.
 */
@RestController
@RequiredArgsConstructor
public class CompanionProductController {

    private final CompanionProductService companionProductService;

    @GetMapping("/api/v1/recommendations/companions")
    public CompanionProductsResponse list(
        @LoginMember AuthMember member,
        @RequestParam Long productId,
        @RequestParam Long childId) {
        return CompanionProductsResponse.from(companionProductService.recommend(member.id(), productId, childId));
    }
}
