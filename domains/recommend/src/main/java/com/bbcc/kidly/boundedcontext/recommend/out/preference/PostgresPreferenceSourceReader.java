package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSourceReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PostgresPreferenceSourceReader implements PreferenceSourceReader {

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
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RecentActivity> findMemberActivities(Long memberId, LocalDateTime from, LocalDateTime to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ConfirmedPurchase> findChildPurchases(Long childProfileId, LocalDateTime from, LocalDateTime to) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<Long, EmbeddingVector> findEmbeddings(Collection<Long> productIds) {
        throw new UnsupportedOperationException();
    }
}
