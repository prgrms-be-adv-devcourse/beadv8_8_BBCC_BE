package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.order.support.QueryCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 관리자 DLT 메시지 목록 · 상세(#601)가 건수와 상관없이 같은 수의 쿼리로 끝나는지 본다.
 * 쿼리 수는 {@link QueryCounter}로 센다(공용 통합 테스트 컨텍스트 그대로, #586).
 */
@IntegrationTest
class OrderDeadLetterQueryCountIntegrationTest {

    private static final int MANY = 10;

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryCounter queryCounter;

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
        return queryCounter.count(query);
    }

    private Long receive(long offset) {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.payment.events", 0, offset, "42",
            "payment.completed", "e-" + offset, "{\"data\":{\"orderId\":42}}",
            "java.lang.IllegalStateException", null, null));
        return jdbcTemplate.queryForObject("select id from orders.order_dead_letter where source_offset = ?",
            Long.class, offset);
    }
}
