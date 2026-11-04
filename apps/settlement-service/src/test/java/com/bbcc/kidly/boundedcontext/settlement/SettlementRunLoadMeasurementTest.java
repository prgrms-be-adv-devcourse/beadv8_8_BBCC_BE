package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정산 실행 부하 측정(#730 · #808). 판매자 1,000명 · 항목 10,000건을 넣고 수동 실행 한 번에 걸리는 시간을 잰다.
 * 고른 분포(판매자마다 10건)와 치우친 분포(판매자 1명이 50%, 9명이 40%, 나머지 990명이 10%)를 따로 잰다(유명인사 문제).
 * 평소 CI에서는 돌지 않는다. 측정할 때만 {@code -Dsettlement.load=true}로 켠다.
 * 예: {@code ./gradlew :apps:settlement-service:integrationTest --tests "*SettlementRunLoadMeasurementTest*"
 *     -Dsettlement.load=true}
 */
@IntegrationTest
@EnabledIfSystemProperty(named = "settlement.load", matches = "true")
class SettlementRunLoadMeasurementTest {

    private static final int SELLERS = 1_000;
    private static final int ITEMS = 10_000;
    private static final Long ADMIN_ID = 3L;
    private static final LocalDate CUTOFF_ON = LocalDate.now();

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("고른 분포: 판매자 1,000명에 10건씩")
    void measureEven() {
        insertItems("고른 분포", i -> 1 + (i % SELLERS));
    }

    // 상위 1명 5,000건(50%), 다음 9명 약 444건씩(40%), 나머지 990명 약 1건씩(10%)
    @Test
    @DisplayName("치우친 분포: 판매자 1명이 50%, 9명이 40%, 990명이 10%")
    void measureSkewed() {
        insertItems("치우친 분포", i -> {
            if (i < ITEMS / 2) {
                return 1L;
            }
            if (i < ITEMS * 9 / 10) {
                return 2L + (i % 9);
            }
            return 11L + (i % (SELLERS - 10));
        });
    }

    private void insertItems(String label, IntToLongFunction sellerOf) {
        insert(sellerOf);

        var started = Instant.now();
        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);
        var elapsed = Duration.between(started, Instant.now());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isEqualTo(SELLERS);
        assertThat(result.failCount()).isZero();
        System.out.printf("[정산 부하 측정 · %s] 판매자 %d명 · 항목 %d건 · 정산서 %d장 · 아웃박스 %d행 · 최대 항목 %d건 · 실행 %d ms%n",
            label, SELLERS, ITEMS, count("settlement.settlement"), count("settlement.settlement_outbox"),
            jdbcTemplate.queryForObject("SELECT max(c) FROM (SELECT count(*) c FROM settlement.settlement_item "
                + "GROUP BY settlement_id) t", Long.class), elapsed.toMillis());
    }

    // 확정 시각은 기준일 전날
    private void insert(IntToLongFunction sellerOf) {
        var confirmedAt = Timestamp.valueOf(CUTOFF_ON.minusDays(1).atTime(12, 0));
        var now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> rows = new ArrayList<>(ITEMS);
        for (int i = 0; i < ITEMS; i++) {
            long orderItemId = 1_000_000L + i;
            long sellerId = sellerOf.applyAsLong(i);
            rows.add(new Object[] {orderItemId, orderItemId, sellerId, 10_000L + (i % 7) * 1_000L,
                confirmedAt, now, now});
        }
        jdbcTemplate.batchUpdate("""
            INSERT INTO settlement.settlement_item
                (order_item_id, order_id, seller_id, sales_amount, status, confirmed_at,
                 created_at, updated_at, version)
            VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, 0)
            """, rows);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
