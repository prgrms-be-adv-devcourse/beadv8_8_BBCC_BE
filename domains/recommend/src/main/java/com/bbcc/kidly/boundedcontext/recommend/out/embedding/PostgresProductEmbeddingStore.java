package com.bbcc.kidly.boundedcontext.recommend.out.embedding;

import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * pgvector가 있는 PostgreSQL에 상품 복사본의 벡터를 저장한다.
 * 벡터 칸은 엔티티가 매핑하지 않아서 엔티티를 고쳐 저장해도 벡터가 덮어써지지 않는다.
 * 확장이 recommend 스키마에 있고 서비스 연결의 search_path에는 없어서, 타입과 연산자 앞에 스키마를 붙인다.
 * 문장 하나가 한 번에 반영되므로 트랜잭션을 열지 않는다.
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
    public boolean saveIfTextUnchanged(Long productId, String text, EmbeddingVector vector) {
        int updated = jdbcClient.sql("""
                UPDATE recommend.replica_product
                SET embedding = CAST(:vector AS recommend.vector),
                    embedded_text_md5 = md5(:text),
                    embedding_attempted_at = NULL
                WHERE product_id = :productId AND embedding_text = :text
                """)
            .param("vector", toLiteral(vector))
            .param("text", text)
            .param("productId", productId)
            .update();
        return updated == 1;
    }

    // pgvector의 문자열 표기 [v1,v2,...]다. Float.toString은 float로 다시 읽으면 같은 값이 되는 가장 짧은 표기를 돌려준다
    private static String toLiteral(EmbeddingVector vector) {
        float[] values = vector.values();
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(values[i]));
        }
        return literal.append(']').toString();
    }
}
