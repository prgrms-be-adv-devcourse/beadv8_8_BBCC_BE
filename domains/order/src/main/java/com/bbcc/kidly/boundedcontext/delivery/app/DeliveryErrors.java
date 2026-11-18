package com.bbcc.kidly.boundedcontext.delivery.app;

import com.bbcc.kidly.boundedcontext.delivery.domain.DeliveryRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;

/**
 * domain의 배송 규칙 위반을 오류 응답용 업무 예외로 바꾼다. 규칙마다 같은 이름의 DeliveryErrorCode로,
 * detail은 그 코드의 기본 설명 그대로다. 컨텍스트 사이에는 코드를 나눠 쓰지 않아(#617) 주문의 OrderErrors를
 * 이 컨텍스트에 따로 둔다.
 */
public final class DeliveryErrors {

    private DeliveryErrors() {
    }

    public static BusinessException toBusiness(DeliveryRuleViolationException violation) {
        return new BusinessException(switch (violation.getRule()) {
            case ALREADY_SHIPPED -> DeliveryErrorCode.ALREADY_SHIPPED;
            case NOT_SHIPPED -> DeliveryErrorCode.NOT_SHIPPED;
            case DELIVERY_CANCELLED -> DeliveryErrorCode.DELIVERY_CANCELLED;
            case ALREADY_CANCELLED -> DeliveryErrorCode.ALREADY_CANCELLED;
        });
    }
}
