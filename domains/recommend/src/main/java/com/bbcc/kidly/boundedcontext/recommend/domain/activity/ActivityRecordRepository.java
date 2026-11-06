package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

import java.util.UUID;

public interface ActivityRecordRepository {

    ActivityRecord save(ActivityRecord activityRecord);

    boolean existsByEventId(UUID eventId);

    // 지운 줄 수를 돌려준다
    void deleteByMemberId(Long memberId);
}
