package com.bbcc.kidly.boundedcontext.chatbot.app.port;

import java.time.LocalDate;

/**
 * 서비스 전체의 하루 챗봇 질문 수를 세어 상한을 넘지 않게 한다.
 */
public interface QuestionQuota {

    /**
     * 그 날짜의 질문 수가 상한보다 작으면 1 올리고 true를, 상한에 닿았으면 올리지 않고 false를 돌려준다. 상한이 0 이하면 세지 않고 false다.
     * 확인과 증가를 한 문장으로 처리해, 챗봇 서버 여러 대가 동시에 세어도 상한을 넘지 않는다.
     *
     * @param date 질문한 날짜(한국 시간)
     */
    boolean tryAcquire(LocalDate date, int dailyLimit);
}
