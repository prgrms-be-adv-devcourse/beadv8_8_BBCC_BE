package com.bbcc.kidly.boundedcontext.delivery.app.port;

/**
 * 이름 붙은 작업을 한 번에 한 서버에서만 실행한다. 주문 컨텍스트의 SingleServerJobRunner와 같은 약속이고,
 * 컨텍스트끼리 나가는 포트를 나눠 쓰지 않으므로 배송 쪽에 따로 둔다. 구현은 out(DB 잠금).
 */
public interface DeliveryJobRunner {

    /**
     * 다른 서버가 같은 작업을 돌고 있지 않으면 job을 실행한다. job은 트랜잭션 밖에서 돈다.
     *
     * @return 실행했으면 true, 다른 서버가 돌고 있어 건너뛰었으면 false
     */
    boolean run(String jobName, Runnable job);
}
