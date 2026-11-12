package com.bbcc.kidly.boundedcontext.recommend.out.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRecommendGrowthRecordRepository
    extends JpaRepository<RecommendGrowthRecord, Long>, RecommendGrowthRecordRepository {

    @Override
    @Modifying
    @Query("delete from RecommendGrowthRecord r where r.growthRecordId = :growthRecordId")
    void deleteByGrowthRecordId(@Param("growthRecordId") Long growthRecordId);

    @Override
    @Query("select r from RecommendGrowthRecord r where r.childProfileId = :childProfileId and r.recordedAt >= :since")
    List<RecommendGrowthRecord> findRecordedSince(@Param("childProfileId") Long childProfileId,
        @Param("since") LocalDateTime since);

    // 자녀 단위(자녀 삭제 이벤트)와 회원 단위(탈퇴 이벤트)는 같은 기록을 동시에 지울 수 있다.
    // 파생 delete는 기록을 읽은 뒤 지우므로 그사이 다른 트랜잭션이 먼저 지우면 커밋 때 낙관적 락 예외가 난다.
    // 한 문장 DELETE는 이미 지워졌어도 0행을 지울 뿐 예외가 없고, 이 처리는 기록 엔티티를 읽지 않으므로 영속성 컨텍스트와 어긋나지 않는다
    @Override
    @Modifying
    @Query("delete from RecommendGrowthRecord r where r.childProfileId = :childProfileId")
    void deleteByChildProfileId(@Param("childProfileId") Long childProfileId);

    // 자녀 복사본에는 삭제 표시된 자녀도 남아 있어 삭제된 자녀의 기록까지 회원 기준으로 찾을 수 있다
    @Override
    @Modifying
    @Query("""
        delete from RecommendGrowthRecord r
        where r.childProfileId in (select c.childProfileId from RecommendReplicaChild c where c.memberId = :memberId)
        """)
    void deleteAllOfMember(@Param("memberId") Long memberId);
}
