package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import tools.jackson.databind.JsonNode;

/**
 * 리뷰 수정 요청. 별점 · 본문을 둘 다 보낸다.
 *
 * @param rating 1~5 정수. JSON 원래 타입 그대로 받는다(소수 · 문자열 · 참거짓이면 400 INVALID_RATING)
 * @param content 공백을 뺀 1~1000자
 */
public record UpdateReviewRequest(JsonNode rating, String content) {

    /**
     * @return 소수부 없는 JSON 숫자면 그 정수, 아니면 null(별점 규칙 위반으로 400)
     */
    public Integer ratingValue() {
        return RatingInput.toInteger(rating);
    }
}
