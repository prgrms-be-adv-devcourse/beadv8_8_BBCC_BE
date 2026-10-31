package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;

/**
 * domain의 장바구니 규칙 위반을 오류 응답용 업무 예외로 바꾼다. 규칙마다 같은 이름의 CartErrorCode로,
 * detail은 그 코드의 기본 설명 그대로다. #617 컨텍스트별 복사.
 */
public final class CartErrors {

    private CartErrors() {
    }

    public static BusinessException toBusiness(CartRuleViolationException violation) {
        return new BusinessException(switch (violation.getRule()) {
            case INVALID_QUANTITY -> CartErrorCode.INVALID_QUANTITY;
        });
    }
}
