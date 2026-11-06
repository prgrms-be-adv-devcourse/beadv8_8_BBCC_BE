package com.bbcc.kidly.boundedcontext.review.domain;

/**
 * 검사에서 걸린 이유 분류(커뮤니티 말). 검사기마다의 분류는 그 어댑터가 이 값으로 옮긴다. 작성자에게는 보이지 않는다.
 */
public enum ModerationReason {
    // 욕설
    PROFANITY,
    // 혐오
    HATE,
    // 성적
    SEXUAL,
    // 폭력
    VIOLENCE,
    // 불법
    ILLEGAL,
    // 개인정보
    PRIVACY,
    // 기타
    OTHER
}
