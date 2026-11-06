package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 한 명의 카테고리별 선호 집계 한 줄이다. 자녀 ID는 FK 없이 값으로만 참조한다.
 * 횟수는 활동을 받을 때 DB가 한 문장으로 올리고 내리므로, 이 클래스는 저장된 줄을 읽는 매핑이다.
 */
@Entity
@Table(schema = "recommend", name = "child_category_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildCategoryPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "activity_count", nullable = false)
    private int activityCount;

    @Column(name = "cart_add_count", nullable = false)
    private int cartAddCount;

    public static ChildCategoryPreference of(Long childProfileId, Long categoryId, int activityCount,
        int cartAddCount) {
        ChildCategoryPreference preference = new ChildCategoryPreference();
        preference.childProfileId = requireNonNull(childProfileId, "childProfileId는 필수입니다");
        preference.categoryId = requireNonNull(categoryId, "categoryId는 필수입니다");
        preference.activityCount = activityCount;
        preference.cartAddCount = cartAddCount;
        return preference;
    }
}
