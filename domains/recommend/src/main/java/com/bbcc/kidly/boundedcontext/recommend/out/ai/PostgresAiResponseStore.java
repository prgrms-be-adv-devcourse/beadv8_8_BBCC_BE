package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 추천 DB의 ai_response 표에 AI 답을 줄마다 저장한다. 응답은 jsonb 칸에 넣고 문자열로 읽는다.
 */
@Component
public class PostgresAiResponseStore implements AiResponseStore {

    private final JdbcClient jdbcClient;

    public PostgresAiResponseStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<String> findLatestSince(AiResponseType type, AiResponseKey key, LocalDateTime createdAfter) {
        return jdbcClient.sql("""
                SELECT response::text
                FROM recommend.ai_response
                WHERE response_type = :type AND input_hash = :hash
                LIMIT 1
                """)
            .param("type", type.name())
            .param("hash", key.value())
            .query(String.class)
            .optional();
    }

    @Override
    public void save(AiResponseType type, AiResponseKey key, String responseJson, LocalDateTime createdAt) {
        jdbcClient.sql("""
                INSERT INTO recommend.ai_response (response_type, input_hash, response, created_at)
                VALUES (:type, :hash, CAST(:response AS jsonb), :createdAt)
                """)
            .param("type", type.name())
            .param("hash", key.value())
            .param("response", responseJson)
            .param("createdAt", createdAt)
            .update();
    }
}
