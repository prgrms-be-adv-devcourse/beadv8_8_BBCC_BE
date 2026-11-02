package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.ReviewQueryService;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.ReviewListResponse;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.ReviewSummaryResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 목록 · 요약 API. 상품 목록 · 요약은 공개, 내 리뷰는 로그인 필요.
 * 오류: 400 INVALID_PRODUCT_ID · INVALID_PAGE_SIZE · INVALID_CURSOR.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewQueryController {

    private final ReviewQueryService reviewQueryService;

    /**
     * 상품 리뷰(최신순).
     *
     * @param productId 필수
     * @param size 1~50, 기본 20
     */
    @GetMapping
    public ReviewListResponse byProduct(@RequestParam(required = false) String productId,
        @RequestParam(required = false) String cursor, @RequestParam(required = false) String size) {
        return ReviewListResponse.from(reviewQueryService.findByProduct(productId, cursor, size));
    }

    /**
     * 내 리뷰(최신순).
     */
    @GetMapping("/me")
    public ReviewListResponse mine(@LoginMember AuthMember member, @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String size) {
        return ReviewListResponse.from(reviewQueryService.findMine(member.id(), cursor, size));
    }

    /**
     * 리뷰 수 · 평균 별점. 리뷰가 없으면 0 · null.
     */
    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@RequestParam(required = false) String productId) {
        var summary = reviewQueryService.summary(productId);
        return new ReviewSummaryResponse(String.valueOf(summary.productId()), summary.reviewCount(),
            summary.averageRating());
    }
}
