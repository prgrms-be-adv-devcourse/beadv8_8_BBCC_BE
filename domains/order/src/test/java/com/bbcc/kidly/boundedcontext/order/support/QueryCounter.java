package com.bbcc.kidly.boundedcontext.order.support;

import com.bbcc.kidly.boundedcontext.order.SchedulerIdle;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 쿼리 수 테스트용(#585): 넘긴 일을 하는 동안 실행된 SQL 문장 수를 센다. Hibernate 통계는 평소 꺼 두고(주문 테스트 설정 그대로)
 * 세는 동안에만 켜므로, 테스트 클래스에 통계 설정을 붙이지 않고 {@link IntegrationTest} 공용 컨텍스트를 그대로 쓴다.
 * <p>
 * 통계는 앱 전체(모든 스레드) 값이라 테스트가 순차로 돈다는 전제다. 같은 이유로 세기 전에 스케줄러가 쉴 때까지 기다린다
 * ({@link SchedulerIdle}, #416). 끝나면 예외가 나도 통계를 끈다.
 */
@Component
@RequiredArgsConstructor
public class QueryCounter {

    private final EntityManagerFactory entityManagerFactory;
    private final ThreadPoolTaskScheduler taskScheduler;

    public long count(Runnable work) {
        Statistics statistics = statistics();
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            work.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    public boolean isCounting() {
        return statistics().isStatisticsEnabled();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
