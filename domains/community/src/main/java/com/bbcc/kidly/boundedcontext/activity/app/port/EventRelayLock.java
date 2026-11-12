package com.bbcc.kidly.boundedcontext.activity.app.port;

/**
 * 이벤트 릴레이를 서버 한 대에서만 하게 하는 잠금. 다른 서버가 쥐고 있으면 기다리지 않고 건너뛴다. 구현은 out/store(DB 잠금).
 */
public interface EventRelayLock {

    /**
     * @return 잠금을 잡아 work를 실행했으면 true, 다른 쪽이 쥐고 있어 건너뛰었으면 false
     */
    boolean runExclusively(Runnable work);
}
