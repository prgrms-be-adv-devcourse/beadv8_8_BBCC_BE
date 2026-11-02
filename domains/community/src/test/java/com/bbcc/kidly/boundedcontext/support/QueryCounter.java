package com.bbcc.kidly.boundedcontext.support;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

/**
 * 쿼리 수 테스트용(주문 모듈 QueryCounter의 경량판): 넘긴 일을 하는 동안 실행된 SQL 문장 수를 센다. Hibernate 통계는 평소 꺼 두고
 * 세는 동안에만 켜므로, 테스트 클래스에 통계 설정을 붙이지 않고 {@link IntegrationTest} 공용 컨텍스트를 그대로 쓴다.
 * 통계는 앱 전체(모든 스레드) 값이라 테스트가 순차로 돈다는 전제다. 커뮤니티에는 스케줄러가 없어 기다리지 않는다.
 * 끝나면 예외가 나도 통계를 끈다.
 */
@Component
@RequiredArgsConstructor
public class QueryCounter {

    private final EntityManagerFactory entityManagerFactory;

    public long count(Runnable work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(true);
        try {
            work.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
