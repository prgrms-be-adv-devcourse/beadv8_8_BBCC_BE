package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.FitInput;
import tools.jackson.databind.JsonNode;

/**
 * 리뷰 작성 요청. 값 확인은 서비스가 한다(형식이 틀리면 400, ReviewErrorCode).
 *
 * @param orderItemId 리뷰 작성 가능 목록의 orderItemId(문자열)
 * @param rating 1~5 정수. JSON 원래 타입 그대로 받는다(소수 · 문자열 · 참거짓이면 400 INVALID_RATING)
 * @param fit 사이즈 핏(선택): SMALL · TRUE_TO_SIZE · LARGE. 키가 없거나 null이면 핏 없이 작성한다
 * @param content 공백을 뺀 1~1000자
 */
public record WriteReviewRequest(String orderItemId, JsonNode rating, String content, JsonNode fit) {

    /**
     * @return 소수부 없는 JSON 숫자면 그 정수, 아니면 null(별점 규칙 위반으로 400)
     */
    public Integer ratingValue() {
        return RatingInput.toInteger(rating);
    }

    /**
     * @return 키 없음 · null · 값 · 틀린 타입을 구분한 핏
     */
    public FitInput fitInput() {
        return FitRequest.toInput(fit);
    }
}
