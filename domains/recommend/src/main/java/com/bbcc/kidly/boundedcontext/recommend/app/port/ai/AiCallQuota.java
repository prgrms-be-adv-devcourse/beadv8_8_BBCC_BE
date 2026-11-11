package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

import java.time.LocalDate;

/**
 * 서비스 전체의 하루 AI 호출 횟수를 세어 상한을 넘지 않게 하는 나가는 연결의 요청서다.
 */
public interface AiCallQuota {

    /**
     * 그 날짜의 호출 횟수가 상한보다 작으면 1 올리고 true를, 상한에 닿았으면 올리지 않고 false를 돌려준다.
     * 상한이 0 이하면 아무것도 세지 않고 false를 돌려준다.
     * 구현은 확인과 증가를 한 문장으로 원자적으로 처리해, 추천 서버 여러 대가 동시에 세어도 상한을 넘지 않게 한다.
     * 트랜잭션(읽기 전용 포함) 밖에서 부른다. 트랜잭션 안에서 부르면 AI 응답을 기다리는 동안 그 날짜의 줄이 잠겨 다른 호출이 모두 기다린다.
     *
     * @param date 호출한 날짜(한국 시간)
     * @param dailyLimit 하루에 허용하는 호출 횟수
     * @return 호출해도 되면 true, 상한에 닿아 호출하면 안 되면 false
     */
    boolean tryAcquire(LocalDate date, int dailyLimit);
}
