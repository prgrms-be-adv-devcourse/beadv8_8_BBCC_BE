package com.bbcc.kidly.boundedcontext.wish.domain;

/**
 * 조르기 규칙 위반. domain은 공통 모듈을 모르므로 이 예외만 던지고, app이 WishErrorCode로 바꿔 오류 응답을 만든다.
 * #617 컨텍스트별 복사(#640).
 */
public class WishRuleViolationException extends RuntimeException {

    /**
     * 위반한 규칙. 이름은 app의 WishErrorCode와 같다.
     */
    public enum Rule {
        INVALID_QUANTITY
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
