package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 같은 카테고리의 상품은 경로가 같으므로, 그 카테고리가 경로에 있는 상품 중 ID가 가장 작은 하나의 경로를 읽는다.
 * 카테고리 경로는 상품의 판매 상태나 삭제 여부와 상관없으므로 상품 상태는 보지 않는다.
 */
@Component
public class PostgresGarmentCategoryPaths implements GarmentCategoryPaths {

    private final JdbcClient jdbcClient;

    public PostgresGarmentCategoryPaths(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<GarmentCategoryPathEntry> findPathOf(Long categoryId) {
        return jdbcClient.sql("""
                SELECT category_id, category_name
                FROM recommend.replica_product_category_path
                WHERE product_id = (
                    SELECT MIN(product_id) FROM recommend.replica_product_category_path WHERE category_id = :categoryId)
                ORDER BY position
                """)
            .param("categoryId", categoryId)
            .query((rs, rowNum) ->
                new GarmentCategoryPathEntry(rs.getLong("category_id"), rs.getString("category_name")))
            .list();
    }
}
