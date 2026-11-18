package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.domain.Fit;
import com.bbcc.kidly.global.error.BusinessException;

/**
 * 요청의 핏 값. 키가 없으면 absent(그대로 둠), null을 보냈으면 cleared(핏을 지움), 문자열이면 text, 그 밖의 타입이면 invalid.
 * 값은 SMALL · TRUE_TO_SIZE · LARGE와 정확히 같아야 한다(대소문자 포함, 아니면 INVALID_FIT).
 */
public record FitInput(boolean given, boolean valid, String text) {

    public static FitInput absent() {
        return new FitInput(false, true, null);
    }

    public static FitInput cleared() {
        return new FitInput(true, true, null);
    }

    public static FitInput text(String text) {
        return new FitInput(true, true, text);
    }

    public static FitInput invalid() {
        return new FitInput(true, false, null);
    }

    Fit toFit() {
        if (!valid) {
            throw new BusinessException(ReviewErrorCode.INVALID_FIT);
        }
        if (text == null) {
            return null;
        }
        try {
            return Fit.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ReviewErrorCode.INVALID_FIT);
        }
    }
}
