package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.TasteSimilarityReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.sql.Types;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 확장이 recommend 스키마에 있어 연산자 앞에 스키마를 붙인다. `<=>`는 코사인 거리라 1에서 빼 유사도로 바꾼다.
 * 상품 ID는 배열 하나로 넘긴다. 목록으로 펼치면 상품 수마다 다른 SQL이 되어 드라이버가 연결마다 따로 캐시하고, 그 캐시가 힙을 채운다.
 * 취향 벡터는 형을 정하지 않은 값으로 넘겨 데이터베이스가 연산자에서 벡터로 읽게 한다. SQL 안에서 글을 벡터로 바꾸면
 * 같은 문장을 되풀이해 일반 계획으로 바뀐 뒤 그 변환을 행마다 다시 해 느려진다.
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
        jdbcClient.sql("""
                SELECT product_id,
                       1 - (embedding OPERATOR(recommend.<=>) :taste) AS similarity
                FROM recommend.replica_product
                WHERE embedding IS NOT NULL AND product_id = ANY(:productIds)
                """)
            .param("taste", new SqlParameterValue(Types.OTHER, VectorText.format(taste)))
            .param("productIds", productIds.toArray(Long[]::new))
            .query(rs -> {
                similarities.put(rs.getLong("product_id"), rs.getDouble("similarity"));
            });
        return similarities;
    }
}
