package com.bbcc.kidly.boundedcontext.wish.domain;

/**
 * 조르기 규칙 위반. domain은 공통 모듈을 모르므로 이 예외만 던지고, app이 WishErrorCode로 바꿔 오류 응답을 만든다.
 * 컨텍스트 사이에는 코드를 나눠 쓰지 않아(#617) 주문의 OrderRuleViolationException과 같은 모양을 조르기 컨텍스트(#640)에 따로 둔다.
 */
public class WishRuleViolationException extends RuntimeException {

    /**
     * 위반한 규칙. 이름은 app의 WishErrorCode와 같다.
     */
    public enum Rule {
        INVALID_WISH_REQUEST,
        WISH_REQUEST_ALREADY_DECIDED
    }

    private final Rule rule;

    public WishRuleViolationException(Rule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }
}
