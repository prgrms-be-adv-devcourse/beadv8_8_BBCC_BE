package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.SettlementServiceApplication;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDailyRunner;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@IntegrationTest
class SettlementDailyRunIntegrationTest {

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private SettlementItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("일회 실행을 두 번 해도 웹 서버 없이 정산서를 한 번만 만든다")
    void runsOnceWithoutWebServer() {
        itemRepository.save(SettlementFixture.item(201L, 10L, 10_000L, LocalDate.now().atStartOfDay().minusHours(1)));

        assertThat(runOnce()).isZero();
        assertThat(runOnce()).isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM settlement.batch_job_execution WHERE status = 'COMPLETED'"))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("정산 Pod가 커밋 뒤 죽으면 재시도가 자동 실행을 닫고 남은 판매자만 정산한다")
    void retriesAfterPodDiesAfterCommit() {
        var confirmedAt = LocalDate.now().atStartOfDay().minusHours(1);
        itemRepository.save(SettlementFixture.item(201L, 10L, 10_000L, confirmedAt));
        assertThat(runOnce()).isZero();
        var firstId = jdbcTemplate.queryForObject(
            "SELECT job_execution_id FROM settlement.batch_job_execution", Long.class);
        jdbcTemplate.update("""
            UPDATE settlement.batch_job_execution
            SET status = 'STARTED', exit_code = 'UNKNOWN', exit_message = '', end_time = NULL, last_updated = now()
            WHERE job_execution_id = ?
            """, firstId);
        jdbcTemplate.update("""
            UPDATE settlement.batch_step_execution
            SET status = 'STARTED', exit_code = 'EXECUTING', end_time = NULL, last_updated = now()
            WHERE job_execution_id = ?
            """, firstId);
        itemRepository.save(SettlementFixture.item(202L, 11L, 10_000L, confirmedAt));

        assertThat(runOnce()).isZero();
        assertThat(count("SELECT count(*) FROM settlement.settlement")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM settlement.settlement_skip")).isZero();
        assertThat(PayoutRequests.all(jdbcTemplate)).extracting(PayoutRequests.Request::sellerId)
            .containsExactly(10L, 11L);
        var first = jdbcTemplate.queryForMap(
            "SELECT status, exit_message FROM settlement.batch_job_execution WHERE job_execution_id = ?", firstId);
        assertThat(first.get("status")).isEqualTo("FAILED");
        assertThat((String) first.get("exit_message")).contains("자동 실행이 앞 Pod가 남긴 자동 실행을 닫음");
        assertThat(count("SELECT count(*) FROM settlement.batch_job_execution WHERE status = 'COMPLETED'"))
            .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM settlement.batch_job_execution")).isEqualTo(2);
        assertThat(count("SELECT count(DISTINCT job_instance_id) FROM settlement.batch_job_execution")).isEqualTo(1);
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private int runOnce() {
        var context = new SpringApplicationBuilder(SettlementServiceApplication.class, BusinessDayConfiguration.class)
            .profiles("test")
            .properties("spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword())
            // CronJob 컨테이너 args와 같다(deploy/k3s/k3s.py)
            .run("--spring.profiles.include=" + SettlementDailyRunner.PROFILE);
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
        return SpringApplication.exit(context);
    }

    // 일회 실행 앱에만 넣는다. @TestConfiguration을 붙이면 @SpringBootTest가 바깥 테스트 컨텍스트에도 넣는다
    static class BusinessDayConfiguration {
        @Bean
        @Primary
        SettlementBusinessCalendar businessDayCalendar() {
            return new SettlementBusinessCalendar(null) {
                @Override
                public boolean isBusinessDay(LocalDate date) {
                    return true;
                }
            };
        }
    }
}
