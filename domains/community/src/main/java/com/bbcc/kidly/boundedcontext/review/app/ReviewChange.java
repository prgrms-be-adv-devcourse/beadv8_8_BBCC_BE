package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.domain.Fit;
import com.bbcc.kidly.boundedcontext.review.domain.Review;

/**
 * 리뷰 수정 요청 값. given이 false면 그 값은 보내지 않은 것(그대로 둔다), true인데 값이 null이면 규칙 위반(400)이다.
 * 컨트롤러는 요청 키 유무를 그대로 옮기는 정식 생성자를, 그 밖에서는 이름 있는 팩토리를 쓴다:
 * {@code ReviewChange.rating(4).withContent("좋은 옷")}, {@code ReviewChange.none().withFit(FitInput.text("LARGE"))}.
 * {@code withRating(null)} · {@code withContent(null)}은 "보냈는데 null"이다.
 */
public record ReviewChange(boolean ratingGiven, Integer rating, boolean contentGiven, String content,
                           FitInput fit, ImageUrlsInput images) {

    /**
     * 아무 값도 보내지 않았다(그대로 수정하면 400 NO_FIELDS_TO_UPDATE).
     */
    public static ReviewChange none() {
        return new ReviewChange(false, null, false, null, FitInput.absent(), ImageUrlsInput.absent());
    }

    /**
     * 별점만 보냈다.
     */
    public static ReviewChange rating(Integer rating) {
        return none().withRating(rating);
    }

    /**
     * 본문만 보냈다.
     */
    public static ReviewChange content(String content) {
        return none().withContent(content);
    }

    public ReviewChange withRating(Integer rating) {
        return new ReviewChange(true, rating, contentGiven, content, fit, images);
    }

    public ReviewChange withContent(String content) {
        return new ReviewChange(ratingGiven, rating, true, content, fit, images);
    }

    public ReviewChange withFit(FitInput fit) {
        return new ReviewChange(ratingGiven, rating, contentGiven, content, fit, images);
    }

    public ReviewChange withImages(ImageUrlsInput images) {
        return new ReviewChange(ratingGiven, rating, contentGiven, content, fit, images);
    }

    Review.Change toDomain(Fit fitValue) {
        return new Review.Change(ratingGiven, rating, contentGiven, content, fit.given(), fitValue,
            images.given());
    }
}
