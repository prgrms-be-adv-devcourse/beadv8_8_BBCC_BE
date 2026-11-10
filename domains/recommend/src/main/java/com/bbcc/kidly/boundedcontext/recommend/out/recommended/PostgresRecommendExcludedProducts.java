package com.bbcc.kidly.boundedcontext.recommend.out.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.port.recommended.RecommendExcludedProducts;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PostgresRecommendExcludedProducts implements RecommendExcludedProducts {

    private final JdbcClient jdbcClient;

    public PostgresRecommendExcludedProducts(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<Long> findIds(Long memberId, Long childProfileId, LocalDateTime cartAddedSince,
        LocalDateTime asOf) {
        return jdbcClient.sql("""
                SELECT a.product_id FROM recommend.activity_record a
                WHERE a.type = 'CART_ITEM_ADDED' AND a.member_id = :memberId AND a.occurred_at >= :cartAddedSince
                  AND a.occurred_at <= :asOf AND a.product_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM recommend.activity_record r
                      WHERE r.type = 'CART_ITEM_REMOVED' AND r.cart_item_id = a.cart_item_id
                        AND r.occurred_at <= :asOf)
                UNION
                SELECT product_id FROM recommend.purchase_record
                WHERE child_profile_id = :childProfileId AND confirmed_at <= :asOf
                """)
            .param("memberId", memberId)
            .param("childProfileId", childProfileId)
            .param("cartAddedSince", cartAddedSince)
            .param("asOf", asOf)
            .query(Long.class)
            .set();
    }
}
