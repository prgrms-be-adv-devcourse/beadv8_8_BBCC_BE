package com.bbcc.kidly.boundedcontext.order.domain;

/**
 * 주문 규칙 위반(입력 · 상태 규칙). 정상 경합은 예외 대신 TransitionResult로 돌려준다.
 * domain은 공통 모듈을 모르므로 이 예외만 던지고, app이 OrderErrorCode로 바꿔 오류 응답을 만든다(#531).
 */
public class OrderRuleViolationException extends RuntimeException {

    /**
     * 위반한 규칙. 이름은 app의 OrderErrorCode와 같다.
     */
    public enum Rule {
        EMPTY_ORDER_ITEMS,
        DUPLICATE_ORDER_ITEM,
        INVALID_QUANTITY,
        PRICE_CHANGED,
        PRODUCT_NOT_PURCHASABLE,
        OWN_PRODUCT,
        CHILD_NOT_ORDERABLE,
        ORDER_ITEM_NOT_FOUND,
        NOT_DELIVERED,
        ALREADY_SHIPPED,
        NOT_SHIPPED,
        DELIVERY_CANCELLED
    }

    private final Rule rule;

    public OrderRuleViolationException(Rule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }
}
