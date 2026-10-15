package com.bbcc.kidly.boundedcontext.recommend.in.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.RecommendedProductResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 자녀에게 맞는 사이즈로 살 수 있는 상품 목록을 신상품순으로 돌려주는 API다.
 */
@RestController
@RequiredArgsConstructor
public class RecommendedProductController {

    private final RecommendedProductService recommendedProductService;

    @GetMapping("/api/v1/children/{childId}/recommended-products")
    public PageResponse<RecommendedProductResponse> list(
        @LoginMember AuthMember member,
        @PathVariable Long childId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
            recommendedProductService.list(member.id(), childId, categoryId, page, size)
                .map(RecommendedProductResponse::from));
    }
}
