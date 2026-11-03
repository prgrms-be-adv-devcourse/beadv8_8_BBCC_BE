package com.bbcc.kidly.boundedcontext.review.app.port;

/**
 * 원래 입력 처리기로 재실행한다. 미지원 · 검증 실패 · 반영 실패는 예외이며 본문은 로그에 남기지 않는다.
 */
public interface CommunityDeadLetterReplay {

    void replay(CommunityDeadLetters.Message message);
}
