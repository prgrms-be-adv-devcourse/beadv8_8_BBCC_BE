package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.SchedulerIdle;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;

/**
 * 관리자 DLT 메시지 목록 · 상세(#601)가 건수와 상관없이 같은 수의 쿼리로 끝나는지 본다.
 * Hibernate 통계는 다른 쿼리 수 테스트와 같은 설정으로 켠다(같은 컨텍스트를 재사용).
 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OrderDeadLetterQueryCountIntegrationTest {

    private static final int MANY = 10;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("목록은 1건이든 10건이든 쿼리 수가 같다(목록 1번 + 건수 1번)")
    void listSameQueryCount() {
        receive(1);
        long single = countStatements(() -> deadLetterService.getDeadLetters(null, null, null, 0, 1));
        for (int offset = 2; offset <= MANY; offset++) {
            receive(offset);
        }
        long many = countStatements(() -> deadLetterService.getDeadLetters(null, null, null, 0, MANY - 1));

        assertThat(many).isEqualTo(single).as("목록 1번 + 건수 1번").isEqualTo(2);
    }

    @Test
    @DisplayName("상세는 DLT 메시지 1번 + 관련 주문 상태 1번")
    void detailQueryCount() {
        Long id = receive(1);

        assertThat(countStatements(() -> deadLetterService.getDeadLetter(id))).isEqualTo(2);
    }

    private long countStatements(Runnable query) {
        // 측정 구간에 스케줄러 쿼리가 섞이지 않게(통계는 모든 스레드를 셈, #416)
        SchedulerIdle.await(taskScheduler);
        statistics.clear();
        query.run();
        return statistics.getPrepareStatementCount();
    }

    private Long receive(long offset) {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events", 0, offset, "42",
            "payment.completed", "e-" + offset, "{\"data\":{\"orderId\":42}}",
            "java.lang.IllegalStateException", null, null));
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = ?",
            Long.class, offset);
    }
}
