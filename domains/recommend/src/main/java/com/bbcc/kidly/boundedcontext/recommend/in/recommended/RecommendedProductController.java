package com.bbcc.kidly.boundedcontext.recommend.in.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductPage;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.RecommendedProductPageResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.RecommendedProductResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 자녀에게 맞는 사이즈로 살 수 있는 상품 목록을 선호 점수 순으로 돌려주는 API다.
 * 2쪽부터는 1쪽 응답의 기준 시각 asOf를 그대로 받아 쪽 사이의 순서를 지킨다.
 */
@RestController
@RequiredArgsConstructor
public class RecommendedProductController {

    private final RecommendedProductService recommendedProductService;

    @GetMapping("/api/v1/recommendations/products")
    public RecommendedProductPageResponse list(
        @LoginMember AuthMember member,
        @RequestParam Long childId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        RecommendedProductPage result =
            recommendedProductService.list(member.id(), childId, categoryId, page, size, asOf);
        return RecommendedProductPageResponse.of(
            PageResponse.from(result.page().map(RecommendedProductResponse::from)), result.asOf());
    }
}
