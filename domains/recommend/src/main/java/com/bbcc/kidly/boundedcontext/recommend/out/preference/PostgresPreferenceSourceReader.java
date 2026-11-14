package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PostgresPreferenceSourceReader implements PreferenceSourceReader {

    // 자녀와 회원은 활동을 고르는 조건만 달라서 같은 쿼리를 쓴다
    private static final String CHILD_ACTIVITIES = "a.child_profile_id = :id";
    private static final String MEMBER_ACTIVITIES = "a.member_id = :id AND a.child_profile_id IS NULL";

    private final JdbcClient jdbcClient;

    public PostgresPreferenceSourceReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<Long> findChildTargets(LocalDateTime activitySince, LocalDateTime purchaseSince,
        long afterChildProfileId, int limit) {
        return jdbcClient.sql("""
                SELECT child_profile_id FROM (
                    SELECT child_profile_id FROM recommend.activity_record
                    WHERE child_profile_id > :after AND occurred_at >= :activitySince
                    UNION
                    SELECT child_profile_id FROM recommend.purchase_record
                    WHERE child_profile_id > :after AND confirmed_at >= :purchaseSince
                ) targets
                WHERE NOT EXISTS (
                    SELECT 1 FROM recommend.replica_child c
                    WHERE c.child_profile_id = targets.child_profile_id AND c.deleted_at IS NOT NULL)
                ORDER BY child_profile_id
                LIMIT :limit
                """)
            .param("after", afterChildProfileId)
            .param("activitySince", activitySince)
            .param("purchaseSince", purchaseSince)
            .param("limit", limit)
            .query(Long.class)
            .list();
    }

    @Override
    public List<Long> findMemberTargets(LocalDateTime activitySince, long afterMemberId, int limit) {
        return jdbcClient.sql("""
                SELECT DISTINCT member_id FROM recommend.activity_record
                WHERE child_profile_id IS NULL AND member_id > :after AND occurred_at >= :activitySince
                ORDER BY member_id
                LIMIT :limit
                """)
            .param("after", afterMemberId)
            .param("activitySince", activitySince)
            .param("limit", limit)
            .query(Long.class)
            .list();
    }

    @Override
    public List<RecentActivity> findChildActivities(Long childProfileId, LocalDateTime from, LocalDateTime to) {
        return findActivities(CHILD_ACTIVITIES, childProfileId, from, to);
    }

    @Override
    public List<RecentActivity> findMemberActivities(Long memberId, LocalDateTime from, LocalDateTime to) {
        return findActivities(MEMBER_ACTIVITIES, memberId, from, to);
    }

    @Override
    public List<ConfirmedPurchase> findChildPurchases(Long childProfileId, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT pr.product_id, p.brand, pr.confirmed_at
                FROM recommend.purchase_record pr
                LEFT JOIN recommend.replica_product p ON p.product_id = pr.product_id
                WHERE pr.child_profile_id = :id AND pr.confirmed_at >= :from AND pr.confirmed_at <= :to
                ORDER BY pr.confirmed_at, pr.id
                """)
            .param("id", childProfileId)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new ConfirmedPurchase(rs.getLong("product_id"), rs.getString("brand"),
                rs.getObject("confirmed_at", LocalDateTime.class)))
            .list();
    }

    @Override
    public Map<Long, EmbeddingVector> findEmbeddings(Collection<Long> productIds) {
        Map<Long, EmbeddingVector> embeddings = new HashMap<>();
        if (productIds.isEmpty()) {
            return embeddings;
        }
        jdbcClient.sql("""
                SELECT product_id, CAST(embedding AS text) AS embedding FROM recommend.replica_product
                WHERE embedding IS NOT NULL AND product_id IN (:productIds)
                """)
            .param("productIds", productIds)
            .query(rs -> {
                embeddings.put(rs.getLong("product_id"), VectorText.parse(rs.getString("embedding")));
            });
        return embeddings;
    }

    // 상세 진입 · 팝업 조회 · 담기 · 검색 결과 클릭 · 조르기 · 찜 기록은 카테고리 · 브랜드 칸을 채우지 않을 수 있어 상품 복사본의 지금 값으로 채운다.
    // 뺀 장바구니 줄의 담기, 철회한 조르기, 해제한 찜은 withdrawn으로 표시해 선호 계산이 무게 0으로 센다
    private List<RecentActivity> findActivities(String ownerCondition, Long id, LocalDateTime from, LocalDateTime to) {
        return jdbcClient.sql("""
                SELECT a.type, a.product_id, COALESCE(a.category_id, p.category_id) AS category_id,
                       COALESCE(a.brand, p.brand) AS brand, a.occurred_at,
                       ((a.type = 'CART_ITEM_ADDED' AND EXISTS (
                           SELECT 1 FROM recommend.activity_record r
                           WHERE r.type = 'CART_ITEM_REMOVED' AND r.cart_item_id = a.cart_item_id
                             AND r.occurred_at <= :to))
                        OR (a.type = 'WISH_SENT' AND EXISTS (
                           SELECT 1 FROM recommend.activity_record w
                           WHERE w.type = 'WISH_CANCELLED' AND w.wish_request_id = a.wish_request_id
                             AND w.occurred_at <= :to))
                        OR (a.type = 'FAVORITE_ADDED' AND EXISTS (
                           SELECT 1 FROM recommend.activity_record f
                           WHERE f.type = 'FAVORITE_REMOVED' AND f.favorite_id = a.favorite_id
                             AND f.occurred_at <= :to))) AS withdrawn
                FROM recommend.activity_record a
                LEFT JOIN recommend.replica_product p ON p.product_id = a.product_id
                WHERE %s
                  AND a.type IN ('SEARCH', 'PRODUCT_VIEWED', 'PRODUCT_QUICK_VIEWED', 'CART_ITEM_ADDED',
                                 'SEARCH_RESULT_CLICKED', 'WISH_SENT', 'FAVORITE_ADDED')
                  AND a.occurred_at >= :from AND a.occurred_at <= :to
                ORDER BY a.occurred_at, a.id
                """.formatted(ownerCondition))
            .param("id", id)
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new RecentActivity(
                ActivityType.valueOf(rs.getString("type")),
                rs.getObject("product_id", Long.class),
                rs.getObject("category_id", Long.class),
                rs.getString("brand"),
                rs.getObject("occurred_at", LocalDateTime.class),
                rs.getBoolean("withdrawn")))
            .list();
    }
}
