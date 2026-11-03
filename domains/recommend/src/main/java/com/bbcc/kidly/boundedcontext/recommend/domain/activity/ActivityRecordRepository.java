package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

import java.util.UUID;

public interface ActivityRecordRepository {

    ActivityRecord save(ActivityRecord activityRecord);

    boolean existsByEventId(UUID eventId);
}
