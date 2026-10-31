package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.boundedcontext.order.domain.OrderRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;

/**
 * domain의 주문 규칙 위반을 오류 응답용 업무 예외로 바꾼다(#531). 규칙마다 같은 이름의 OrderErrorCode로,
 * detail은 그 코드의 기본 설명 그대로라 응답(상태 · title · detail)은 domain이 BusinessException을 던지던 때와 같다.
 */
public final class OrderErrors {

    private OrderErrors() {
    }

    public static BusinessException toBusiness(OrderRuleViolationException violation) {
        return new BusinessException(switch (violation.getRule()) {
            case EMPTY_ORDER_ITEMS -> OrderErrorCode.EMPTY_ORDER_ITEMS;
            case DUPLICATE_ORDER_ITEM -> OrderErrorCode.DUPLICATE_ORDER_ITEM;
            case INVALID_QUANTITY -> OrderErrorCode.INVALID_QUANTITY;
            case PRICE_CHANGED -> OrderErrorCode.PRICE_CHANGED;
            case PRODUCT_NOT_PURCHASABLE -> OrderErrorCode.PRODUCT_NOT_PURCHASABLE;
            case OWN_PRODUCT -> OrderErrorCode.OWN_PRODUCT;
            case CHILD_NOT_ORDERABLE -> OrderErrorCode.CHILD_NOT_ORDERABLE;
            case ORDER_ITEM_NOT_FOUND -> OrderErrorCode.ORDER_ITEM_NOT_FOUND;
            case NOT_DELIVERED -> OrderErrorCode.NOT_DELIVERED;
        });
    }
}
