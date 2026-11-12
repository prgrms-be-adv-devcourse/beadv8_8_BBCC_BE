package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementProperties;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.outbox.SettlementOutboxRelayService;
import com.bbcc.kidly.boundedcontext.settlement.domain.outbox.SettlementOutboxRepository;
import com.bbcc.kidly.support.KafkaIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정산 실행 부하 측정(#730 · #808). 연휴 뒤 첫 영업일 실행이 밀린 날수(days)만큼의 구매확정을 한 번에 정산해도
 * 운영 설정 안에 끝나는지 잰다. 이 측정이 실행 제한(CronJob activeDeadlineSeconds)과 {@code stale-run-after}의 근거다.
 * <ul>
 *   <li>하루치: 2025년 아동·유아용품 온라인쇼핑 거래액 5조 4,515억 원(국가데이터처 온라인쇼핑동향, 2026-02-02)을
 *       365일과 1회 평균 결제 44,300원(오픈서베이 2026)으로 나눈 약 33.7만 건. 국내 시장 전체를 받는다는 상한이다</li>
 *   <li>days: 기본 6. 2026년 영업일 중 가장 많이 밀리는 날인 설 연휴(2/16~18) 뒤 2/19(목)가 2/13(금)~2/18(수) 6일치를 받는다.
 *       실행은 그날 0시 전에 확정된 대기 항목을 모두 정산하므로 연휴 전날 확정분도 포함된다</li>
 *   <li>판매자 1,000명에 고르게 나눈다. 치우친 분포(유명인사 판매자)는 #808에서 고른 분포보다 빨랐다. 성공 수 = 판매자 수를
 *       검사하므로 하위 판매자 몫이 판매자 수보다 적으면 생성한 데이터 때문에 실패한다(운영 결함이 아니다)</li>
 * </ul>
 * 기준: 가장 오래 걸린 묶음 &lt; {@code stale-run-after}, 실행 전체 &lt; CronJob 실행 제한, 실행 뒤 미발행 아웃박스 0건.
 * 아웃박스 소진 시간은 실행이 끝난 뒤 쌓인 미발행분을 실제 Kafka로 보내는 시간이다(릴레이 실행 시간 합 + 운영 주기 × (횟수 - 1)).
 * 운영에서는 실행 도중에도 릴레이가 돌므로 이보다 짧다.
 * 수 분 걸려 평소 CI에서는 돌지 않는다. 측정할 때만 환경 변수로 켠다({@code -D} 시스템 속성은 테스트 JVM으로 넘어가지 않는다).
 * {@code SETTLEMENT_LOAD_DAYS} · {@code SETTLEMENT_LOAD_DAILY_ITEMS}로 규모를 바꾼다.
 * 예: {@code SETTLEMENT_LOAD=true ./gradlew :apps:settlement-service:integrationTest
 *     --tests "*SettlementRunLoadMeasurementTest*" --rerun}
 */
@KafkaIntegrationTest
@EnabledIfEnvironmentVariable(named = "SETTLEMENT_LOAD", matches = "true")
class SettlementRunLoadMeasurementTest {

    private static final int SELLERS = 1_000;
    private static final int DAYS = Integer.parseInt(System.getenv().getOrDefault("SETTLEMENT_LOAD_DAYS", "6"));
    private static final int DAILY_ITEMS = Integer.parseInt(
        System.getenv().getOrDefault("SETTLEMENT_LOAD_DAILY_ITEMS", "337147"));
    private static final int INSERT_BATCH = 10_000;
    private static final Long ADMIN_ID = 3L;
    private static final LocalDate CUTOFF_ON = LocalDate.now();
    // CronJob settlement-daily-run의 activeDeadlineSeconds(deploy/k3s/k3s.py). 파드 기동 시간도 이 안에 든다
    private static final Duration RUN_DEADLINE = Duration.ofSeconds(7_200);
    // 운영 릴레이 주기(settlement.yml outbox.relay-interval 기본값). 테스트 설정은 릴레이를 꺼 두므로 운영값을 쓴다
    private static final Duration RELAY_INTERVAL = Duration.ofSeconds(1);

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementProperties properties;

    @Autowired
    private SettlementOutboxRelayService relayService;

    @Autowired
    private SettlementOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("연휴 뒤 밀린 구매확정을 실행 제한 안에 정산하고 지급 요청을 모두 보낸다")
    void measure() {
        int items = DAILY_ITEMS * DAYS;
        insert(items);

        var startedAt = LocalDateTime.now();
        var result = settlementRunService.runManually(CUTOFF_ON, ADMIN_ID);
        var run = Duration.between(startedAt, LocalDateTime.now());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.successCount()).isEqualTo(SELLERS);
        assertThat(result.failCount()).isZero();
        var maxChunk = maxChunkGap(startedAt);
        var drain = drainOutbox();
        System.out.printf("[정산 부하 측정 · %d일치] 판매자 %d명 · 항목 %d건 · 최대 항목 %d건 · 실행 %d ms · "
                + "최장 묶음 %d ms · 아웃박스 %d행 · 소진 %d ms(릴레이 %d회)%n",
            DAYS, SELLERS, items, maxItemsPerSettlement(), run.toMillis(), maxChunk.toMillis(),
            count("settlement.settlement_outbox"), drain.elapsed().toMillis(), drain.relays());
        assertThat(maxChunk).isLessThan(properties.staleRunAfter());
        assertThat(run).isLessThan(RUN_DEADLINE);
        assertThat(outboxRepository.countUnsent()).isZero();
    }

    // 확정 시각은 기준일 전 DAYS일에 고르게 나눈다(그날 정오)
    private void insert(int items) {
        var now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> rows = new ArrayList<>(INSERT_BATCH);
        for (int i = 0; i < items; i++) {
            long orderItemId = 1_000_000L + i;
            var confirmedAt = Timestamp.valueOf(CUTOFF_ON.minusDays(1 + i % DAYS).atTime(12, 0));
            rows.add(new Object[] {orderItemId, orderItemId, 1L + i % SELLERS, 10_000L + (i % 7) * 1_000L,
                confirmedAt, now, now});
            if (rows.size() == INSERT_BATCH || i == items - 1) {
                jdbcTemplate.batchUpdate("""
                    INSERT INTO settlement.settlement_item
                        (order_item_id, order_id, seller_id, sales_amount, status, confirmed_at,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, 0)
                    """, rows);
                rows.clear();
            }
        }
    }

    // 묶음(chunk)마다 정산서를 같은 시각으로 만드므로(SettlementSellerWriter), 실행 시작부터 정산서 생성 시각 사이
    // 가장 긴 간격이 가장 오래 걸린 묶음이다. 진행(step 갱신) 없이 stale-run-after가 지나면 다음 실행이 이 실행을 닫는다
    private Duration maxChunkGap(LocalDateTime startedAt) {
        var times = new ArrayList<LocalDateTime>();
        times.add(startedAt);
        times.addAll(jdbcTemplate.queryForList(
            "SELECT DISTINCT created_at FROM settlement.settlement ORDER BY created_at", LocalDateTime.class));
        var max = Duration.ZERO;
        for (int i = 1; i < times.size(); i++) {
            var gap = Duration.between(times.get(i - 1), times.get(i));
            if (gap.compareTo(max) > 0) {
                max = gap;
            }
        }
        return max;
    }

    private Drain drainOutbox() {
        var busy = Duration.ZERO;
        int relays = 0;
        while (outboxRepository.countUnsent() > 0) {
            var started = System.nanoTime();
            var result = relayService.relay();
            busy = busy.plusNanos(System.nanoTime() - started);
            relays++;
            assertThat(result.sent()).isPositive();
        }
        return new Drain(busy.plus(RELAY_INTERVAL.multipliedBy(Math.max(0, relays - 1))), relays);
    }

    private record Drain(Duration elapsed, int relays) {
    }

    private long maxItemsPerSettlement() {
        return jdbcTemplate.queryForObject("SELECT max(c) FROM (SELECT count(*) c FROM settlement.settlement_item "
            + "GROUP BY settlement_id) t", Long.class);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
