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
 * 자녀 한 명의 브랜드별 선호 집계 한 줄이다. 자녀 ID는 FK 없이 값으로만 참조한다.
 * 횟수는 활동을 받을 때 DB가 한 문장으로 올리고 내리므로, 이 클래스는 저장된 줄을 읽는 매핑이다.
 */
@Entity
@Table(schema = "recommend", name = "child_brand_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildBrandPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String brand;

    @Column(name = "activity_count", nullable = false)
    private int activityCount;

    public static ChildBrandPreference of(Long childProfileId, String brand, int activityCount) {
        requireNonNull(brand, "brand는 필수입니다");
        if (!PreferenceActivity.isBrandPresent(brand)) {
            throw new IllegalArgumentException("브랜드는 비어 있을 수 없습니다");
        }
        ChildBrandPreference preference = new ChildBrandPreference();
        preference.childProfileId = requireNonNull(childProfileId, "childProfileId는 필수입니다");
        preference.brand = brand;
        preference.activityCount = activityCount;
        return preference;
    }
}
