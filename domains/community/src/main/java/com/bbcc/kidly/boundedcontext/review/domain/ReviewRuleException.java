package com.bbcc.kidly.boundedcontext.review.domain;

/**
 * 리뷰 규칙 위반. 메시지에는 규칙 이름만 넣는다(본문 · 닉네임을 넣지 않는다).
 */
public class ReviewRuleException extends RuntimeException {

    private final ReviewRule rule;

    public ReviewRuleException(ReviewRule rule) {
        super(rule.name());
        this.rule = rule;
    }

    public ReviewRule rule() {
        return rule;
    }
}
