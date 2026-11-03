package com.bbcc.kidly.boundedcontext.recommend.out.activity;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaActivityRecordRepository extends JpaRepository<ActivityRecord, Long>, ActivityRecordRepository {
}
