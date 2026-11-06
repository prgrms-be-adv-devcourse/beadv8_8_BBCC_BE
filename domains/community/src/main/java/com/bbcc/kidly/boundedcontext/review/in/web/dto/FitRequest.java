package com.bbcc.kidly.boundedcontext.review.in.web.dto;

import com.bbcc.kidly.boundedcontext.review.app.ReviewService.FitInput;
import tools.jackson.databind.JsonNode;

/**
 * 요청 본문의 fit을 JSON 원래 타입 그대로 받아 구분한다: 키 없음(그대로 둠) · null(핏 지움) · 문자열(값) · 그 밖(400 INVALID_FIT).
 */
public final class FitRequest {

    private FitRequest() {
    }

    public static FitInput toInput(JsonNode fit) {
        if (fit == null || fit.isMissingNode()) {
            return FitInput.absent();
        }
        if (fit.isNull()) {
            return FitInput.cleared();
        }
        return fit.isString() ? FitInput.text(fit.stringValue()) : FitInput.invalid();
    }
}
