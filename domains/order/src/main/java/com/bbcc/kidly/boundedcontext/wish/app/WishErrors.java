package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;

/**
 * domain의 조르기 규칙 위반을 오류 응답용 업무 예외로 바꾼다. 규칙마다 같은 이름의 WishErrorCode로,
 * detail은 그 코드의 기본 설명 그대로다. #617 컨텍스트별 복사(#640).
 */
public final class WishErrors {

    private WishErrors() {
    }

    public static BusinessException toBusiness(WishRuleViolationException violation) {
        return new BusinessException(switch (violation.getRule()) {
            case INVALID_QUANTITY -> WishErrorCode.INVALID_QUANTITY;
        });
    }
}
