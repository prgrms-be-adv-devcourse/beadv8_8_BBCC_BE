package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.FitInput;
import tools.jackson.databind.JsonNode;

/**
 * 리뷰 수정 요청(일부 수정). 보낸 값만 바꾼다: 키가 없으면 그대로 두고, 키를 보냈는데 null이거나 타입이 틀리면 400
 * (INVALID_RATING · INVALID_CONTENT · INVALID_FIT). 별점 · 본문 · 핏 셋 다 없으면 400 NO_FIELDS_TO_UPDATE.
 *
 * @param rating 1~5 정수. JSON 원래 타입 그대로 받는다(소수 · 문자열 · 참거짓 · null이면 400 INVALID_RATING)
 * @param fit 사이즈 핏(선택): SMALL · TRUE_TO_SIZE · LARGE. PATCH에서 null이면 핏을 지운다
 * @param content 공백을 뺀 1~1000자 문자열(null · 문자열 아님이면 400 INVALID_CONTENT)
 */
public record UpdateReviewRequest(JsonNode rating, JsonNode content, JsonNode fit) {

    /**
     * 키가 없으면 Java null, 키를 null로 보내면 JSON null 노드라 둘을 구분한다.
     */
    public boolean ratingGiven() {
        return rating != null;
    }

    /**
     * @return 소수부 없는 JSON 숫자면 그 정수, 아니면 null(별점 규칙 위반으로 400)
     */
    public Integer ratingValue() {
        return RatingInput.toInteger(rating);
    }

    public boolean contentGiven() {
        return content != null;
    }

    /**
     * @return JSON 문자열이면 그 값, 아니면 null(본문 규칙 위반으로 400)
     */
    public String contentValue() {
        return content != null && content.isString() ? content.stringValue() : null;
    }

    /**
     * @return 키 없음 · null · 값 · 틀린 타입을 구분한 핏
     */
    public FitInput fitInput() {
        return FitRequest.toInput(fit);
    }
}
