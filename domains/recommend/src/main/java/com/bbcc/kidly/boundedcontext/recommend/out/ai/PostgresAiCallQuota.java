package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 추천 DB의 ai_call_usage 표에 날짜마다 한 줄로 하루 AI 호출 횟수를 센다.
 */
@Component
public class PostgresAiCallQuota implements AiCallQuota {

    private final JdbcClient jdbcClient;

    public PostgresAiCallQuota(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean tryAcquire(LocalDate date, int dailyLimit) {
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
}
