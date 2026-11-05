package com.bbcc.kidly.boundedcontext.recommend.out.activity;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaActivityRecordRepository extends JpaRepository<ActivityRecord, Long>, ActivityRecordRepository {

    // 파생 delete는 행을 하나씩 읽어 지우므로, 한 번의 DELETE로 지우도록 쿼리를 직접 쓴다
    @Override
    @Modifying
    @Query("delete from ActivityRecord r where r.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
