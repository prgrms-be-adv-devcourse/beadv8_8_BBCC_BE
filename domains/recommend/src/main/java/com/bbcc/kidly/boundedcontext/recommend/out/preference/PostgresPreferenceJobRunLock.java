package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceJobRunLock;
import com.bbcc.kidly.boundedcontext.recommend.out.lock.PostgresSessionLock;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 선호 Job 실행 잠금이다. 추천 서비스는 서버가 여러 대라 한 대만 계산해야 같은 대상을 여러 번 계산하지 않는다.
 */
@Component
public class PostgresPreferenceJobRunLock implements PreferenceJobRunLock {

    private final PostgresSessionLock lock;

    public PostgresPreferenceJobRunLock(DataSource dataSource) {
        this.lock = new PostgresSessionLock(dataSource, "kidly.recommend.preference-job", "선호 Job");
    }

    @Override
    public boolean runExclusively(Runnable work) {
        return lock.runExclusively(work);
    }
}
