package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.BrandAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.CategoryAffinity;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.TasteVector;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀와 회원은 표 이름과 키 칸만 달라서 {@link Owner}로 묶어 같은 SQL을 쓴다.
 * 벡터 타입은 확장이 있는 recommend 스키마를 붙여 쓴다.
 */
@Component
public class PostgresPreferenceSnapshotStore implements PreferenceSnapshotStore {

    private record Owner(String keyColumn, String categoryTable, String brandTable, String vectorTable) {
    }

    private static final Owner CHILD = new Owner("child_profile_id", "recommend.child_category_affinity",
        "recommend.child_brand_affinity", "recommend.child_taste_vector");
    private static final Owner MEMBER = new Owner("member_id", "recommend.member_category_affinity",
        "recommend.member_brand_affinity", "recommend.member_taste_vector");

    private final JdbcClient jdbcClient;

    public PostgresPreferenceSnapshotStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceChild(Long childProfileId, PreferenceSnapshot snapshot) {
        replace(CHILD, childProfileId, snapshot);
    }

    @Override
    @Transactional
    public void replaceMember(Long memberId, PreferenceSnapshot snapshot) {
        replace(MEMBER, memberId, snapshot);
    }

    @Override
    public PreferenceSnapshot findChild(Long childProfileId) {
        return find(CHILD, childProfileId);
    }

    @Override
    public PreferenceSnapshot findMember(Long memberId) {
        return find(MEMBER, memberId);
    }

    private void replace(Owner owner, Long id, PreferenceSnapshot snapshot) {
        deleteAll(owner, id);
        LocalDateTime calculatedAt = snapshot.calculatedAt();
        for (CategoryAffinity category : snapshot.categories()) {
            jdbcClient.sql("INSERT INTO " + owner.categoryTable() + " (" + owner.keyColumn()
                    + ", category_id, score, calculated_at) VALUES (:id, :categoryId, :score, :calculatedAt)")
                .param("id", id)
                .param("categoryId", category.categoryId())
                .param("score", category.score())
                .param("calculatedAt", calculatedAt)
                .update();
        }
        for (BrandAffinity brand : snapshot.brands()) {
            jdbcClient.sql("INSERT INTO " + owner.brandTable() + " (" + owner.keyColumn()
                    + ", brand, score, calculated_at) VALUES (:id, :brand, :score, :calculatedAt)")
                .param("id", id)
                .param("brand", brand.brand())
                .param("score", brand.score())
                .param("calculatedAt", calculatedAt)
                .update();
        }
        TasteVector taste = snapshot.tasteVector();
        if (taste != null) {
            jdbcClient.sql("INSERT INTO " + owner.vectorTable() + " (" + owner.keyColumn()
                    + ", embedding, weight_sum, activity_count, calculated_at)"
                    + " VALUES (:id, CAST(:embedding AS recommend.vector), :weightSum, :activityCount, :calculatedAt)")
                .param("id", id)
                .param("embedding", VectorText.format(taste.embedding()))
                .param("weightSum", taste.weightSum())
                .param("activityCount", taste.activityCount())
                .param("calculatedAt", calculatedAt)
                .update();
        }
    }

    private void deleteAll(Owner owner, Long id) {
        for (String table : List.of(owner.categoryTable(), owner.brandTable(), owner.vectorTable())) {
            jdbcClient.sql("DELETE FROM " + table + " WHERE " + owner.keyColumn() + " = :id")
                .param("id", id)
                .update();
        }
    }

    private PreferenceSnapshot find(Owner owner, Long id) {
        LocalDateTime calculatedAt = latestCalculatedAt(owner, id);
        if (calculatedAt == null) {
            return PreferenceSnapshot.empty();
        }
        List<CategoryAffinity> categories = jdbcClient.sql("SELECT category_id, score FROM "
                + owner.categoryTable() + " WHERE " + owner.keyColumn() + " = :id ORDER BY category_id")
            .param("id", id)
            .query((rs, rowNum) -> new CategoryAffinity(rs.getLong("category_id"), rs.getDouble("score")))
            .list();
        List<BrandAffinity> brands = jdbcClient.sql("SELECT brand, score FROM " + owner.brandTable()
                + " WHERE " + owner.keyColumn() + " = :id ORDER BY brand")
            .param("id", id)
            .query((rs, rowNum) -> new BrandAffinity(rs.getString("brand"), rs.getDouble("score")))
            .list();
        List<TasteVector> tastes = jdbcClient.sql("SELECT CAST(embedding AS text) AS embedding, weight_sum,"
                + " activity_count FROM " + owner.vectorTable() + " WHERE " + owner.keyColumn() + " = :id")
            .param("id", id)
            .query((rs, rowNum) -> new TasteVector(VectorText.parse(rs.getString("embedding")),
                rs.getDouble("weight_sum"), rs.getInt("activity_count")))
            .list();
        TasteVector taste = null;
        if (!tastes.isEmpty()) {
            taste = tastes.get(0);
        }
        return new PreferenceSnapshot(categories, brands, taste, calculatedAt);
    }

    // 줄이 하나도 없으면 null이다
    private LocalDateTime latestCalculatedAt(Owner owner, Long id) {
        String where = " WHERE " + owner.keyColumn() + " = :id";
        return jdbcClient.sql("SELECT max(calculated_at) FROM (SELECT calculated_at FROM " + owner.categoryTable()
                + where + " UNION ALL SELECT calculated_at FROM " + owner.brandTable() + where
                + " UNION ALL SELECT calculated_at FROM " + owner.vectorTable() + where + ") rows")
            .param("id", id)
            .query((rs, rowNum) -> rs.getObject(1, LocalDateTime.class))
            .optional()
            .orElse(null);
    }
}
