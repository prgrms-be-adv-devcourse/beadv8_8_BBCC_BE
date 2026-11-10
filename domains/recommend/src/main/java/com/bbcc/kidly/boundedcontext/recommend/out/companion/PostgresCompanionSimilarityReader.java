package com.bbcc.kidly.boundedcontext.recommend.out.companion;

import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionSimilarityReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 두 상품의 벡터를 자바로 가져오지 않고 데이터베이스에서 바로 잰다.
 * 확장이 recommend 스키마에 있어 연산자 앞에 스키마를 붙이고, `<=>`는 코사인 거리라 1에서 빼 유사도로 바꾼다.
 */
@Component
public class PostgresCompanionSimilarityReader implements CompanionSimilarityReader {

    private final JdbcClient jdbcClient;

    public PostgresCompanionSimilarityReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<Long, Double> similarities(Long viewedProductId, Collection<Long> productIds) {
        Map<Long, Double> similarities = new HashMap<>();
        if (productIds.isEmpty()) {
            return similarities;
        }
        jdbcClient.sql("""
                SELECT c.product_id,
                       1 - (c.embedding OPERATOR(recommend.<=>) v.embedding) AS similarity
                FROM recommend.replica_product c
                JOIN recommend.replica_product v ON v.product_id = :viewedProductId
                WHERE v.embedding IS NOT NULL AND c.embedding IS NOT NULL AND c.product_id IN (:productIds)
                """)
            .param("viewedProductId", viewedProductId)
            .param("productIds", productIds)
            .query(rs -> {
                similarities.put(rs.getLong("product_id"), rs.getDouble("similarity"));
            });
        return similarities;
    }
}
