package com.bbcc.kidly.boundedcontext.favorite.domain;

/**
 * 찜 규칙 위반. 메시지에는 규칙 이름만 넣는다.
 */
public class FavoriteRuleException extends RuntimeException {

    private final FavoriteRule rule;

    public FavoriteRuleException(FavoriteRule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public FavoriteRule rule() {
        return rule;
    }
}
