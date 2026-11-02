package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.ReviewEligibilityQueryService;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.EligibleReviewsResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 작성 자격 API. 로그인한 회원 본인의 자격만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewEligibilityController {

    private final ReviewEligibilityQueryService reviewEligibilityQueryService;

    /**
     * 리뷰를 쓸 수 있는 내 주문상품(구매확정 최신순). size · cursor 형식이 틀리면 400(INVALID_PAGE_SIZE · INVALID_CURSOR).
     *
     * @param cursor 직전 응답의 nextCursor. 처음이면 뺀다
     * @param size 1~50, 기본 20
     */
    @GetMapping("/eligible")
    public EligibleReviewsResponse eligible(
        @LoginMember AuthMember member,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String size) {
        return EligibleReviewsResponse.from(reviewEligibilityQueryService.findEligible(member.id(), cursor, size));
    }
}
