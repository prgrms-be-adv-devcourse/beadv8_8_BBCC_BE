package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.ReviewResponse;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.UpdateReviewRequest;
import com.bbcc.kidly.boundedcontext.review.in.web.dto.WriteReviewRequest;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 작성 · 수정 · 삭제 API. 로그인한 회원 본인의 리뷰만 다룬다.
 */
@RestController
@RequestMapping(ReviewController.REVIEWS_PATH)
@RequiredArgsConstructor
public class ReviewController {

    public static final String REVIEWS_PATH = "/api/v1/reviews";

    private final ReviewService reviewService;

    /**
     * 201 + Location. 오류: 400 INVALID_RATING · INVALID_CONTENT · INVALID_ORDER_ITEM_ID · INVALID_FIT ·
     * FIT_NOT_APPLICABLE · INVALID_IMAGE_URL · TOO_MANY_IMAGES, 403 REVIEW_NOT_ELIGIBLE, 409 REVIEW_ALREADY_WRITTEN.
     */
    @PostMapping
    public ResponseEntity<ReviewResponse> write(@LoginMember AuthMember member,
        @RequestBody WriteReviewRequest request) {
        ReviewResponse body = ReviewResponse.from(reviewService.write(member.id(), member.nickname(),
            request.orderItemId(), request.ratingValue(), request.content(), request.fitInput(),
            request.imageUrlsInput()));
        return ResponseEntity.created(URI.create(REVIEWS_PATH + "/" + body.reviewId())).body(body);
    }

    /**
     * 200. 보낸 값(별점 · 본문 · 핏 · 사진)만 바꾼다(fit: null은 핏을 지운다, imageUrls는 목록 전체 교체 · 빈 목록은 모두 삭제).
     * 오류: 400 NO_FIELDS_TO_UPDATE · INVALID_RATING · INVALID_CONTENT · INVALID_FIT · FIT_NOT_APPLICABLE ·
     * INVALID_IMAGE_URL · TOO_MANY_IMAGES, 403 REVIEW_FORBIDDEN, 404 REVIEW_NOT_FOUND.
     */
    @PatchMapping("/{reviewId}")
    public ReviewResponse update(@LoginMember AuthMember member, @PathVariable Long reviewId,
        @RequestBody UpdateReviewRequest request) {
        return ReviewResponse.from(reviewService.update(member.id(), reviewId, new ReviewService.ReviewChange(
            request.ratingGiven(), request.ratingValue(), request.contentGiven(), request.contentValue(),
            request.fitInput(), request.imageUrlsInput())));
    }

    /**
     * 204. 오류: 403 REVIEW_FORBIDDEN, 404 REVIEW_NOT_FOUND.
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@LoginMember AuthMember member, @PathVariable Long reviewId) {
        reviewService.delete(member.id(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
