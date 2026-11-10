package com.bbcc.kidly.boundedcontext.recommend.out.activity;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaActivityRecordRepository extends JpaRepository<ActivityRecord, Long>, ActivityRecordRepository {

    // 파생 delete는 행을 하나씩 읽어 지우므로, 한 번의 DELETE로 지우도록 쿼리를 직접 쓴다
    @Override
    @Modifying
    @Query("delete from ActivityRecord r where r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // 엔티티를 거치지 않아 감사 시각이 채워지지 않으므로 생성 · 수정 시각은 활동 시각으로 넣는다
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO recommend.activity_record
            (event_id, type, member_id, child_profile_id, product_id, occurred_at, created_at, updated_at)
        VALUES
            (:eventId, 'PRODUCT_VIEWED', :memberId, :childProfileId, :productId, :occurredAt, :occurredAt, :occurredAt)
        ON CONFLICT DO NOTHING
        """)
    int insertProductViewedIfAbsent(
        @Param("eventId") UUID eventId,
        @Param("memberId") Long memberId,
        @Param("childProfileId") Long childProfileId,
        @Param("productId") Long productId,
        @Param("occurredAt") LocalDateTime occurredAt);

    @Override
    default boolean saveProductViewedIfAbsent(ActivityRecord activityRecord) {
        requireNonNull(activityRecord, "activityRecord는 필수입니다");
        if (activityRecord.getType() != ActivityType.PRODUCT_VIEWED) {
            throw new IllegalArgumentException("상세 진입 기록만 저장할 수 있습니다");
        }
        int inserted = insertProductViewedIfAbsent(
            activityRecord.getEventId(),
            activityRecord.getMemberId(),
            activityRecord.getChildProfileId(),
            activityRecord.getProductId(),
            activityRecord.getOccurredAt());
        return inserted == 1;
    }
}
