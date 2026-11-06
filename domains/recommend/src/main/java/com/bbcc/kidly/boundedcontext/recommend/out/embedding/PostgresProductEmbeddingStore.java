package com.bbcc.kidly.boundedcontext.recommend.out.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * pgvector가 있는 PostgreSQL에 상품 복사본의 벡터를 저장한다.
 * 확장이 recommend 스키마에 있고 서비스 연결의 search_path에는 없어서, 타입과 연산자 앞에 스키마를 붙인다.
 */
@Component
public class PostgresProductEmbeddingStore implements ProductEmbeddingStore {

    private final JdbcClient jdbcClient;

    public PostgresProductEmbeddingStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ProductEmbeddingTarget> findStale(int limit) {
        return jdbcClient.sql("""
                SELECT product_id, embedding_text, embedding_attempted_at IS NOT NULL AS retried
                FROM recommend.replica_product
                WHERE embedding_text IS NOT NULL
                  AND embedded_text_md5 IS DISTINCT FROM md5(embedding_text)
                ORDER BY embedding_attempted_at NULLS FIRST, product_id
                LIMIT :limit
                """)
            .param("limit", limit)
            .query((rs, rowNum) -> new ProductEmbeddingTarget(
                rs.getLong("product_id"), rs.getString("embedding_text"), rs.getBoolean("retried")))
            .list();
    }

    @Override
    public void markAttempted(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        jdbcClient.sql("UPDATE recommend.replica_product SET embedding_attempted_at = :now"
                + " WHERE product_id IN (:productIds)")
            .param("now", LocalDateTime.now())
            .param("productIds", productIds)
            .update();
    }

    @Override
    public boolean saveIfTextUnchanged(Long productId, String text, EmbeddingVector vector) {
        int updated = jdbcClient.sql("""
                UPDATE recommend.replica_product
                SET embedding = CAST(:vector AS recommend.vector),
                    embedded_text_md5 = md5(:text),
                    embedding_attempted_at = NULL
                WHERE product_id = :productId AND embedding_text = :text
                """)
            .param("vector", Arrays.toString(vector.values()))
            .param("text", text)
            .param("productId", productId)
            .update();
        return updated == 1;
    }
}
