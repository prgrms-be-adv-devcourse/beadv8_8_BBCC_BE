package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.domain.Review;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 리뷰 한 건(응답용).
 */
public record ReviewView(
    Long reviewId,
    Long orderItemId,
    Long productId,
    int rating,
    String content,
    String nickname,
    boolean authorWithdrawn,
    String fit,
    String purchasedSize,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

    static ReviewView from(Review review, String purchasedSize, List<String> imageUrls) {
        boolean withdrawn = review.isAuthorWithdrawn();
        return new ReviewView(review.getId(), review.getOrderItemId(), review.getProductId(), review.getRating(),
            review.getContent(), AuthorDisplay.nickname(review.getNickname(), withdrawn), withdrawn,
            review.getFit() == null ? null : review.getFit().name(), purchasedSize, List.copyOf(imageUrls),
            review.getCreatedAt(), review.getUpdatedAt());
    }
}
