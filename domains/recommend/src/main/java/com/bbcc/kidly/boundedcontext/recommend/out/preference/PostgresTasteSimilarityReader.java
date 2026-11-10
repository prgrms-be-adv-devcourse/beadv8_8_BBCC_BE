package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.TasteSimilarityReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 확장이 recommend 스키마에 있어 벡터 타입과 연산자 앞에 스키마를 붙인다. `<=>`는 코사인 거리라 1에서 빼 유사도로 바꾼다.
 */
@Component
public class PostgresTasteSimilarityReader implements TasteSimilarityReader {

    private final JdbcClient jdbcClient;

    public PostgresTasteSimilarityReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<Long, Double> similarities(EmbeddingVector taste, Collection<Long> productIds) {
        Map<Long, Double> similarities = new HashMap<>();
        if (productIds.isEmpty()) {
            return similarities;
        }
        List<Map.Entry<Long, Double>> rows = jdbcClient.sql("""
                SELECT product_id, 1 - (embedding OPERATOR(recommend.<=>) CAST(:taste AS recommend.vector)) AS similarity
                FROM recommend.replica_product
                WHERE embedding IS NOT NULL AND product_id IN (:productIds)
                """)
            .param("taste", VectorText.format(taste))
            .param("productIds", productIds)
            .query((rs, rowNum) -> Map.entry(rs.getLong("product_id"), rs.getDouble("similarity")))
            .list();
        for (Map.Entry<Long, Double> row : rows) {
            similarities.put(row.getKey(), row.getValue());
        }
        return similarities;
    }
}
