package com.bbcc.kidly.boundedcontext.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * 쿼리 수 도우미(#585): 공용 통합 테스트 컨텍스트(통계 설정 없음) 그대로 실행한 쿼리 수를 세고, 끝나면 통계를 끈다.
 */
@IntegrationTest
class QueryCounterIntegrationTest {

    private static final int QUERIES = 3;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("공용 컨텍스트는 통계 설정이 없고 평소 통계가 꺼져 있다(테스트 클래스에 설정을 붙이지 않는다)")
    void commonContextWithoutStatisticsSetting() {
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.generate_statistics")).isNull();
        assertThat(queryCounter.isCounting()).isFalse();
    }

    @Test
    @DisplayName("쿼리를 N번 실행하면 N을 돌려주고, 끝나면 통계를 끈다")
    void countsQueries() {
        long count = queryCounter.count(() -> runQueries(QUERIES));

        assertThat(count).isEqualTo(QUERIES);
        assertThat(queryCounter.isCounting()).isFalse();
    }

    @Test
    @DisplayName("쿼리 없이 끝나면 0이다(이전 측정 값이 남지 않는다)")
    void startsFromZero() {
        queryCounter.count(() -> runQueries(QUERIES));

        assertThat(queryCounter.count(() -> { })).isZero();
    }

    @Test
    @DisplayName("일이 예외로 끝나도 예외를 그대로 던지고 통계를 끈다")
    void stopsCountingOnException() {
        IllegalStateException failure = new IllegalStateException("실패");

        assertThatThrownBy(() -> queryCounter.count(() -> {
            runQueries(1);
            throw failure;
        })).isSameAs(failure);
        assertThat(queryCounter.isCounting()).isFalse();
    }

    private void runQueries(int times) {
        for (int i = 0; i < times; i++) {
            entityManager.createNativeQuery("select 1").getSingleResult();
        }
    }
}
