package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreferenceRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 카테고리 표와 브랜드 표를 함께 다루므로 Spring Data 리포지토리 하나로 만들 수 없어, 표마다 리포지토리를 두고 이 클래스가 묶는다.
 * 쓰기는 호출한 쪽의 트랜잭션 안에서 실행한다.
 */
@Repository
@RequiredArgsConstructor
class JpaChildPreferenceRepository implements ChildPreferenceRepository {

    private final JpaChildCategoryPreferenceRepository categoryPreferences;

    private final JpaChildBrandPreferenceRepository brandPreferences;

    @Override
    public ChildPreference findByChildProfileId(Long childProfileId) {
        return ChildPreference.of(
            categoryPreferences.findAllByChildProfileId(childProfileId),
            brandPreferences.findAllByChildProfileId(childProfileId));
    }

    @Override
    public void add(Long childProfileId, PreferenceActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        if (activity.hasCategory()) {
            int cartAddDelta = activity.cartAdd() ? 1 : 0;
            categoryPreferences.addCounts(childProfileId, activity.categoryId(), 1, cartAddDelta, now);
        }
        if (activity.hasBrand()) {
            brandPreferences.addCount(childProfileId, activity.brand(), 1, now);
        }
    }
}
