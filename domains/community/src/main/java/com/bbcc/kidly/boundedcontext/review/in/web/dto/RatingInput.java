package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

/**
 * 요청 본문의 별점을 JSON 원래 타입 그대로 받아 정수로 바꾼다. 앱 JSON 설정은 소수 · 문자열을 정수로 바꿔 읽으므로(4.5 → 4)
 * 별점은 이 변환을 거친다: 소수부 없는 JSON 숫자만 정수로 넘기고(4.0 → 4), 소수 · 문자열 · 참거짓 · null · 빠짐은 null로 넘겨
 * 별점 규칙 위반(400 INVALID_RATING)이 되게 한다. 범위(1~5)는 리뷰 규칙이 본다.
 */
public final class RatingInput {

    private RatingInput() {
    }

    public static Integer toInteger(JsonNode rating) {
        if (rating == null || !rating.isNumber()) {
            return null;
        }
        BigDecimal value = rating.decimalValue();
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            // 소수부가 있거나 int 범위 밖: 별점이 아니다
            return null;
        }
    }
}
