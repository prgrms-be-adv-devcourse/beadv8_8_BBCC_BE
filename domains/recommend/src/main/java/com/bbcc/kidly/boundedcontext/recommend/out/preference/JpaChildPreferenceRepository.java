package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreference;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ChildPreferenceRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceActivity;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 표마다 Spring Data 리포지토리를 두어 각 표의 증감 쿼리를 그 표의 리포지토리에 두고, 이 클래스가 두 표를 한 집계로 묶는다.
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
        change(childProfileId, activity, 1);
    }

    @Override
    public void subtract(Long childProfileId, PreferenceActivity activity) {
        change(childProfileId, activity, -1);
    }

    private void change(Long childProfileId, PreferenceActivity activity, int delta) {
        LocalDateTime now = LocalDateTime.now();
        if (activity.hasCategory()) {
            int cartAddDelta = activity.cartAdd() ? delta : 0;
            categoryPreferences.addCounts(childProfileId, activity.categoryId(), delta, cartAddDelta, now);
        }
        if (activity.hasBrand()) {
            brandPreferences.addCount(childProfileId, activity.brand(), delta, now);
        }
    }
}
