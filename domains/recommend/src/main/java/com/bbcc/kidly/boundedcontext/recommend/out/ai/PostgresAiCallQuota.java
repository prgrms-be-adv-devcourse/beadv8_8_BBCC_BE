package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 추천 DB의 ai_call_usage 표로 구현한 {@link AiCallQuota}다.
 */
@Component
public class PostgresAiCallQuota implements AiCallQuota {

    private final JdbcClient jdbcClient;

    public PostgresAiCallQuota(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean tryAcquire(LocalDate date, int dailyLimit) {
        if (dailyLimit <= 0) {
            return false;
        }
        int changed = jdbcClient.sql("""
                INSERT INTO recommend.ai_call_usage (usage_date, call_count)
                VALUES (:date, 1)
                ON CONFLICT (usage_date) DO UPDATE
                SET call_count = recommend.ai_call_usage.call_count + 1
                WHERE recommend.ai_call_usage.call_count < :limit
                """)
            .param("date", date)
            .param("limit", dailyLimit)
            .update();
        return changed == 1;
    }

    @Override
    public int usedOn(LocalDate date) {
        return jdbcClient.sql("SELECT call_count FROM recommend.ai_call_usage WHERE usage_date = :date")
            .param("date", date)
            .query(Integer.class)
            .optional()
            .orElse(0);
    }
}
