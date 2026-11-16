package com.bbcc.kidly.boundedcontext.chatbot.out.persistence;

import com.bbcc.kidly.boundedcontext.chatbot.app.port.QuestionQuota;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 챗봇 DB의 question_usage 표로 센다. 트랜잭션 없이 문장 하나로 끝나 그 날짜의 줄을 오래 잠그지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PostgresQuestionQuota implements QuestionQuota {

    private final JdbcClient jdbcClient;

    @Override
    public boolean tryAcquire(LocalDate date, int dailyLimit) {
        if (dailyLimit <= 0) {
            return false;
        }
        int changed = jdbcClient.sql("""
                INSERT INTO chatbot.question_usage (usage_date, question_count)
                VALUES (:date, 1)
                ON CONFLICT (usage_date) DO UPDATE
                SET question_count = chatbot.question_usage.question_count + 1
                WHERE chatbot.question_usage.question_count < :limit
                """)
            .param("date", date)
            .param("limit", dailyLimit)
            .update();
        return changed == 1;
    }
}
