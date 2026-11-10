package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

import java.util.UUID;

public interface ActivityRecordRepository {

    ActivityRecord save(ActivityRecord activityRecord);

    /**
     * 같은 회원 · 자녀(없으면 회원) · 상품의 상세 진입 기록이 같은 날 이미 있으면 저장하지 않는다.
     *
     * @return 저장했으면 true, 이미 있어서 건너뛰었으면 false
     */
    boolean saveProductViewedIfAbsent(ActivityRecord activityRecord);

    boolean existsByEventId(UUID eventId);

    void deleteByMemberId(Long memberId);
}
