package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildCategoryPreference;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaChildCategoryPreferenceRepository extends JpaRepository<ChildCategoryPreference, Long> {

    List<ChildCategoryPreference> findAllByChildProfileId(Long childProfileId);

    // 줄이 없으면 만들고 있으면 횟수에 더한다. 읽고 더하고 쓰면 동시에 오는 활동의 몫이 사라지므로 한 문장으로 한다
    // JPA Auditing이 동작하지 않는 네이티브 쿼리라서 createdAt · updatedAt을 쿼리에서 직접 넣는다
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into recommend.child_category_preference
            (child_profile_id, category_id, activity_count, cart_add_count, created_at, updated_at)
        values (:childProfileId, :categoryId, :activityDelta, :cartAddDelta, :now, :now)
        on conflict (child_profile_id, category_id) do update
        set activity_count = recommend.child_category_preference.activity_count + excluded.activity_count,
            cart_add_count = recommend.child_category_preference.cart_add_count + excluded.cart_add_count,
            updated_at = excluded.updated_at
        """)
    void addCounts(@Param("childProfileId") Long childProfileId, @Param("categoryId") Long categoryId,
        @Param("activityDelta") int activityDelta, @Param("cartAddDelta") int cartAddDelta,
        @Param("now") LocalDateTime now);
}
