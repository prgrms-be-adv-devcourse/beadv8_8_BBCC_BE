package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.ReviewView;
import java.time.LocalDateTime;

/**
 * 리뷰 한 건. ID는 문자열, 시각은 LocalDateTime(한국 시간).
 *
 * @param nickname 작성 시점 닉네임. 몰랐으면 null
 */
public record ReviewResponse(
    String reviewId,
    String orderItemId,
    String productId,
    int rating,
    String content,
    String nickname,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

    public static ReviewResponse from(ReviewView view) {
        return new ReviewResponse(String.valueOf(view.reviewId()), String.valueOf(view.orderItemId()),
            String.valueOf(view.productId()), view.rating(), view.content(), view.nickname(), view.createdAt(),
            view.updatedAt());
    }
}
