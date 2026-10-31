package com.bbcc.kidly.boundedcontext.delivery.domain;

/**
 * 배송 규칙 위반. domain은 공통 모듈을 모르므로 이 예외만 던지고, app이 DeliveryErrorCode로 바꿔 오류 응답을 만든다.
 * #617 컨텍스트 분리로 주문 OrderRuleViolationException에서 배송 규칙을 옮겼다.
 */
public class DeliveryRuleViolationException extends RuntimeException {

    /**
     * 위반한 규칙. 이름은 app의 DeliveryErrorCode와 같다.
     */
    public enum Rule {
        ALREADY_SHIPPED,
        NOT_SHIPPED,
        DELIVERY_CANCELLED
    }

    private final Rule rule;

    public DeliveryRuleViolationException(Rule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }
}
