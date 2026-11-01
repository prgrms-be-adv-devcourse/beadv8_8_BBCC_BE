package com.bbcc.kidly.boundedcontext.order.app.port;

/**
 * 이름 붙은 작업을 한 번에 한 서버에서만 실행한다(#564). 서버가 여러 대여도 같은 이름의 작업은 동시에 하나만 돈다.
 * 구현은 out/lock(DB 잠금). 스케줄러 · 서비스는 DB 종류를 모른다.
 */
public interface SingleServerJobRunner {

    /**
     * 다른 서버가 같은 작업을 돌고 있지 않으면 job을 실행한다. job은 트랜잭션 밖에서 돈다(그 안의 처리 트랜잭션 경계는 그대로).
     * job이 던진 예외는 그대로 던진다(잠금은 풀린다).
     *
     * @param jobName 작업 이름(서버 사이에 같은 작업을 가리킨다)
     * @return 실행했으면 true, 다른 서버가 돌고 있어 이번에는 건너뛰었으면 false
     */
    boolean run(String jobName, Runnable job);
}
