package com.bbcc.kidly.boundedcontext.recommend.app.port.preference;

/**
 * 서버가 여러 대여도 선호 Job을 한 대만 실행하게 하는 잠금의 요청서다.
 *
 * <p>같은 기준일의 첫 실행은 Spring Batch의 작업 인스턴스 유일 제약이 하나만 만든다.
 * 하지만 실행 기록을 만드는 격리 수준이 기본값(READ COMMITTED)이라, 실패한 기준일을 두 서버가 동시에 다시 실행하면 실행이 둘 생길 수 있다.
 * 서버가 죽어 실행 중으로 남은 기록을 정리할 때도 다른 서버가 돌고 있지 않음을 알아야 한다.
 */
public interface PreferenceJobRunLock {

    /**
     * 잠금을 잡고 work를 실행한 뒤 푼다.
     *
     * @return 잠금을 잡아 실행했으면 true, 다른 서버가 쥐고 있어 건너뛰었으면 false
     */
    boolean runExclusively(Runnable work);
}
