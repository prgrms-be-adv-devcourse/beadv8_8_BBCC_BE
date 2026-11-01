package com.bbcc.kidly.boundedcontext.cart.domain;

/**
 * 장바구니 규칙 위반. domain은 공통 모듈을 모르므로 이 예외만 던지고, app이 CartErrorCode로 바꿔 오류 응답을 만든다.
 * #617 컨텍스트별 복사(주문의 OrderRuleViolationException에서 이 컨텍스트 규칙만).
 */
public class CartRuleViolationException extends RuntimeException {

    /**
     * 위반한 규칙. 이름은 app의 CartErrorCode와 같다.
     */
    public enum Rule {
        INVALID_QUANTITY,
        OWN_PRODUCT
    }

    private final Rule rule;

    public CartRuleViolationException(Rule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }
}
